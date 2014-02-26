## Motivation

Fills a local installation of [Mediawiki](http://www.mediawiki.org/wiki/MediaWiki) with the data from NCBI-Gene. It allow people in your lab to annotate *their* genes on the local wiki.


## Screenshot

![screenshot mediawiki](https://raw.github.com/lindenb/localgenewiki/master/doc/screenshot01.jpg)



### Input files
the input is a **XML-formatted** **NCBI-Gene** file. It can be obtained via NCBI Efetch e.g:

```bash
$ 	curl -o genes.xml "http://www.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=gene&id=111,112,113,114,115&rettype=xml"
```

or by downloading the binary files from the ncbi  (e.g: ftp://ftp.ncbi.nlm.nih.gov/gene/DATA/ASN_BINARY/Mammalia/Homo_sapiens.ags.gz ) and converting them to XML with **gene2xml** ( ftp://ftp.ncbi.nih.gov/asn1-converters/by_program/gene2xml/ )


### Output

The program uses the media wiki API to create/overwrite some [Mediawiki Templates](http://www.mediawiki.org/wiki/Help:Templates) for each gene. For example here is the content for the page `Template:Gene113`

```html
<includeonly>
<div>
<dl>
<dt>NCBI GeneID</dt>
<dd>113</dd>
<dt>Official Symbol</dt>
<dd>[http://www.ncbi.nlm.nih.gov/gene/113 ADCY7]</dd>

(...)


</includeonly><noinclude>This is a [http://www.mediawiki.org/wiki/Help:Templates template] for the NCBI gene '''ADCY7''' ID.113. To use this template insert <br>
<span style="background-color:black; color:white; font-size:150%;">
<nowiki>{{GeneADCY7}}</nowiki>
</span>
<br> in body of the article.
An article about '''ADCY7''' should be located at :[[ADCY7|ADCY7]]
[[Category:Ncbi gene templates]]</noinclude>
```
If an article for this gene doesn't already exist, it will be created. For example here is the simple content for **ADCY7** (ncbi gene 113)
```
{{Gene113}}
```

For the synonyms of the genes, pages with redirection/disambiguation will be created if they don't already exist.

#### Customizing the output

The mediawiki code is generated using **XSLT**. The default stylesheet is 

https://github.com/lindenb/localgenewiki/blob/master/src/main/resources/META-INF/gene2wiki.xsl 

You can change this stylesheet or provide another on the command line to fulfills your needs.


# Requirements

* java 1.7
* apache maven 2.2.1 http://maven.apache.org
* It was tested with MediaWiki http://www.mediawiki.org/wiki/MediaWiki 1.22.2

# Installation and Compilation

```bash
$ git clone https://github.com/lindenb/localgenewiki.git
$ cd localgenewiki
$ mvn package
```

# Synopsis

```
$ java -jar target/localgenewiki-0.1.jar [options] (stdin|ncgi-gene.xml)
```
# Options

<table>
<tr><th>Option</th><th>Description</th></tr>
<tr><td>-h</td><td> help; This screen.</td></tr>
<tr><td>-u (user)</td><td>mediawiki user.login</td></tr>
<tr><td>-p (password)</td><td>mediawiki user.password,or asked later on command line)</td></tr>
<tr><td>-a </td><td><api.url> e.g. http://en.wikipedia.org/w/api.php</td></tr>
<tr><td>-c </td><td>create article if it doesn't exist default: false</td></tr>
<tr><td>-r </td><td>create alias (disambigation/redirect) if it doesn't exist default: false</td></tr>
<tr><td>-y (file.xsl) </td><td> reads an alternat xslt stylesheet. (optional)</td></tr>
<tr><td>-debug</td><td>turns log off. (optional)</td></tr>
<tr><td>-T (dir)</td><td>tmp directory. (optional)</td></tr>
</table>

## Example:

```bash

$ java -jar target/localgenewiki-0.1.jar \
		-u WikiSysop -p pass12345 \
		-a http://localhost/wiki/mediawiki-1.22.2/api.php \
		-c -r \
		genes.xml

```


## References / See also

* http://en.wikipedia.org/wiki/Gene_Wiki
* Huss JW; Lindenbaum P; Martone M et al. (January 2010). "The Gene Wiki: community intelligence applied to human gene annotation". Nucleic Acids Res. 38 (Database issue): D633–9. http://www.ncbi.nlm.nih.gov/pubmed/19755503



## Author

Pierre Lindenbaum PhD.

[@yokofakun](https://twitter.com/yokofakun)

http://plindenbaum.blogspot.com

