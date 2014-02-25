.PHONY=all tests

all:tests

tests: genes.xml
	mvn package && \
	java -jar target/localgenewiki-0.1.jar \
		-u WikiSysop -p adminadmin \
		-a http://localhost/~lindenb/mediawiki-1.22.2/api.php \
		-c -r \
		$<
		
		
genes.xml:
	curl -o $@ "http://www.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=gene&id=111,112,113,114,115&rettype=xml"
