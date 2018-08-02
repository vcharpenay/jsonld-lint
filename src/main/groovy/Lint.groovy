import groovy.json.*
import groovy.transform.Immutable

import org.eclipse.rdf4j.common.lang.FileFormat
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.repository.util.Repositories
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.rio.RDFWriter
import org.eclipse.rdf4j.rio.Rio
import org.eclipse.rdf4j.sail.memory.MemoryStore

class Lint {
	
	static final BASE = 'thisiswrong:'
	
	static final DOCUMENT_GRAPH = "tag:document"
	
	static final VOCABULARY_GRAPH = "tag:vocabulary"
	
	static final CURIE_REGEX = '^[A-Za-z0-9]*:[A-Za-z0-9]*$'
	
	static final CURIE_OR_TERM_REGEX = '^[A-Za-z0-9]*:?[A-Za-z0-9]*$'
	
	static final Q1 = """\
    select distinct ?term where {
		graph <${DOCUMENT_GRAPH}> { ?s ?p ?o . }
	    filter (isIRI(?s) && regex(str(?s), \"${CURIE_REGEX}\"))
	    bind (str(?s) as ?term)
	}
	"""
	
	static final Q2 = """\
    select distinct ?term where {
		graph <${DOCUMENT_GRAPH}> { ?s ?p ?o . }
	    filter (regex(str(?p), \"${CURIE_OR_TERM_REGEX}\"))
	    bind (str(?p) as ?term)
	}
	"""
	
	static final Q3 = """\
	select distinct ?term where {
		graph <${DOCUMENT_GRAPH}> { ?s ?p ?o . }
	    filter (isIRI(?o) && regex(str(?o), \"${CURIE_REGEX}\"))
	    bind (str(?o) as ?term)
	}
	"""
	
	static final Q4 = """\
	select distinct ?term where {
		graph <${DOCUMENT_GRAPH}> { ?s ?p ?o . }
	    filter (isLiteral(?o) && regex(str(?o), \"${CURIE_REGEX}\"))
	    bind (str(?o) as ?term)
	}
	"""
	
	static final Q5 = """\
	prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
	prefix owl: <http://www.w3.org/2002/07/owl#>
	select distinct ?term where {
		graph <${DOCUMENT_GRAPH}> { ?s ?p ?o . }
		filter not exists {
			graph <${VOCABULARY_GRAPH}> {
				?p a ?prop .
			    values ?prop {
					rdf:Property
					owl:ObjectProperty
					owl:DatatypeProperty
					owl:AnnotationProperty
			    }
			}
		}
	    bind (str(?p) as ?term)
	}
	"""
	
	static final Q6 = """\
	prefix owl: <http://www.w3.org/2002/07/owl#>
	prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
	select distinct ?term where {
		graph <${DOCUMENT_GRAPH}> { ?s a ?t . }
		filter not exists {
			graph <${VOCABULARY_GRAPH}> {
				?t a ?cl .
			    values ?cl { rdfs:Class owl:Class }
			}
		}
	    bind (str(?t) as ?term)
	}
	"""
	
	static final defaultRules = [
		[message: 'IRI includes potential unresolved namespace', withVocab: false, query: Q1],
		[message: 'Term or namespace potentially not in @context', withVocab: false, query: Q2],
		[message: 'IRI includes potential unresolved namespace', withVocab: false, query: Q3],
		[message: 'Potential IRI defined as literal (use @type:@id or @vocab)', withVocab: false, query: Q4],
		[message: 'Term maps to a predicate with no RDFS/OWL property definition', withVocab: true, query: Q5],
		[message: 'Term maps to a class with no RDFS/OWL class definition', withVocab: true, query: Q6]
	]
	
	final SailRepository repo
	
	final rules
	
	Lint(String doc, ...vocabs) {
		repo = new SailRepository(new MemoryStore())
		repo.initialize()
		
		ValueFactory factory = SimpleValueFactory.getInstance()
		def docGraph = factory.createIRI(DOCUMENT_GRAPH)
		def vocabGraph = factory.createIRI(VOCABULARY_GRAPH)
		
		RepositoryConnection con = repo.getConnection()
		
		con.add(new FileInputStream(doc), BASE, RDFFormat.JSONLD, docGraph)
		
		def filtered = vocabs.findAll({ vocab ->
			def opt = Rio.getParserFormatForFileName(vocab)
			
			if (!opt.isPresent()) {
				println('Warning: vocabulary ignored (format not recognized): ' + vocab)
				return false // filtered out
			} else {
				con.add(new FileInputStream(vocab), BASE, opt.get(), vocabGraph)
				return true // kept in
			}
		})
			
		rules = filtered ? defaultRules : defaultRules.findAll({ r -> !r.withVocab })
	}
	
	public validate() {
		rules.collect({ r -> report(r) })
	}
	
	private report(rule) {
		Repositories.tupleQuery(repo, rule.query, { result ->
			while (result.hasNext()) {
				def term = result.next().getValue('term').stringValue()
				println(term + ' -> ' + rule.message)
			}
		})
	}
	
	static void main(String[] args) {
		if (args.length < 1) {
			println('Usage: jsonld-lint <jsonLdDocument> [<vocabularyDocument>]*')
			return
		}
		
		def doc = args[0]
		
		def vocabs = []
		for (def i = 1; i < args.length; i++) vocabs[i - 1] = args[i]
		
		def l = new Lint(doc, *vocabs)
		l.validate()
	}
	
}