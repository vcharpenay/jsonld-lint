# JSON-LD Lint

Command-line utility to detect common mistakes in JSON-LD documents.

Usage: `jsonld-lint <jsonLdDocument> [<vocabularyDocument>]*`.

The following cases are tested:

 - IRI includes potential unresolved namespace
 - Term or namespace potentially not in @context
 - IRI includes potential unresolved namespace
 - Potential IRI defined as literal (use @type:@id or @vocab)
 - Term maps to a predicate with no RDFS/OWL property definition
 - Term maps to a class with no RDFS/OWL class definition

The following ones are planned:

- Missing values (count object number and keys in object)
- Context not defined (no triple generated)
- Invalid literal data type (e.g. xsd:integer not a number or with comma)
