/**
 *
 * LocalGeneWiki
 * 
 * Author:
 * 		Pierre Lindenbaum PhD
 * Mail:
 *		plindenbaum@yahoo.fr
 * WWW:
 * 		http://plindenbaum.blogspot.com
 */
package com.github.lindenb.localgenewiki;

import java.io.ByteArrayInputStream;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
/**

CD8B    CD8b molecule
CD8BP    CD8b molecule pseudogene
CD9    CD9 molecule
CD14    CD14 molecule
CD19    CD19 molecule
MS4A1    membrane-spanning 4-domains, subfamily A, member 1
MS4A3    membrane-spanning 4-domains, subfamily A, member 3 (hematopoietic cell-specific)
CD22    CD22 molecule
CD24    CD24 molecule

*/

import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;



/**
 *
 * LocalGeneWiki
 *
 */
public class LocalGeneWiki
    {
    /** logger */
    private static Logger LOG=Logger.getLogger(LocalGeneWiki.class.getName());
    /** Apche Http Client */
    private HttpClient client=new HttpClient();
    /** mediawiki login */
    private String mwLogin=null;
    /** mediawiki password */
    private String mwPassword=null;


    /** MW API URL */
    private String mwApiUrl="http://localhost/api.php";
    /** prefix of the Template: pages */
    private String templatePrefix="Gene";
    /** shall we create an article if it doesn't exist ? */
    private boolean createArticle=false;
    /** shall we create an alias if it doesn't exist ? */
    private boolean createAliases=false;
    /** namespace for articles */
    private String articleNamespace="";
    /** XML document builder */
    private DocumentBuilder domBuilder=null;
    /** XPATH processor */
    private XPath xpath=null;

    /** alternate XSLT stylesheet */
    private File altXsltStylesheet=null;
    
    private Environment environment=null;
    private Database aliasTopage=null;
    
    /** serialize xml to String */
    private  Transformer serializeXml;
    private Transaction txn=null;
    
    private XMLInputFactory xmlInputFactory;
    
    private static class Page
    	{
        String starttimestamp=null;
        String edittoken=null;
        boolean missing=false;
    	}
    
    private static class Locus
    	implements Comparable<Locus>
    	{
    	String locus;
    	String description="";
    	@Override
    	public int compareTo(Locus o) {
    		return locus.compareTo(o.locus);
    		}
    	@Override
    	public int hashCode() {
    		return locus.hashCode();
    		}
    	@Override
    	public boolean equals(Object obj) {
    		return locus.equals(Locus.class.cast(obj).locus);
    		}
    	}
    
    
    /** constructor */
    private LocalGeneWiki()
	    {
    	try
	    	{
		    //build the XML builder
		    DocumentBuilderFactory fact=DocumentBuilderFactory.newInstance();
		    fact.setNamespaceAware(false);
		    fact.setCoalescing(true);
		    fact.setIgnoringComments(true);
		    fact.setIgnoringElementContentWhitespace(true);
		    this.domBuilder=fact.newDocumentBuilder();

		    this.domBuilder.setEntityResolver(new EntityResolver()
		    	{
				@Override
				public InputSource resolveEntity(String arg0, String arg1)
						throws SAXException, IOException
					{
					LOG.info("resolving "+arg0+" "+arg1);
					return new InputSource(new StringReader(""));
					}
				});
		    //build the xpath processor
		    XPathFactory xf=XPathFactory.newInstance();
		    this.xpath=xf.newXPath();
		    
		    /* build xml2string */
	        TransformerFactory trFactory=TransformerFactory.newInstance();
	        this.serializeXml=trFactory.newTransformer();
	        this.serializeXml.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
	        
	        this.xmlInputFactory=XMLInputFactory.newFactory();
	        xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
	        xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
	        xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
	        xmlInputFactory.setXMLResolver(new XMLResolver() {
				@Override
				public Object resolveEntity(String arg0, String arg1, String arg2,
						String arg3) throws XMLStreamException {
					return new ByteArrayInputStream(new byte[0]);
				}
			});
	     
		    }
    	catch(Exception err)
    		{
    		err.printStackTrace();
    		System.exit(-1);
    		}
	    }
    
    private String localizedArticle(String title)
    	{
    	return this.articleNamespace.isEmpty()?title:this.articleNamespace+":"+title;
    	}
    
    private Page parsePage(PostMethod method) throws IOException,XMLStreamException
    	{
    	final QName att_starttimestamp = new QName("starttimestamp");
    	final QName att_edittoken = new QName("edittoken");
    	final QName att_missing = new QName("missing");
    	
    	
    	InputStream in=method.getResponseBodyAsStream();
    	Page page=null;
    	XMLEventReader r=xmlInputFactory.createXMLEventReader(in);
    	
    	while(r.hasNext())
    		{
    		XMLEvent evt=r.nextEvent();
    		if(!evt.isStartElement()) continue;
    		StartElement E=evt.asStartElement();
    		if(!E.getName().getLocalPart().equals("page")) continue;
    		page=new Page();
    		Attribute att=E.getAttributeByName(att_starttimestamp);
            page.starttimestamp=(att==null?null:att.getValue());
            att=E.getAttributeByName(att_edittoken);
            page.edittoken=(att==null?null:att.getValue());
            att=E.getAttributeByName(att_missing);
            page.missing=(att!=null);
    		break;
    		}
    	r.close();
    	in.close();
    	if(page==null) throw new XMLStreamException("<page> not found.");
    	return page;
    	}
    
    /** get credentials from mediawiki */
    private void login() throws IOException,SAXException,XPathExpressionException
        {
        LOG.info("logging as "+this.mwLogin);
        String mwToken=null;
        while(true)
            {
            PostMethod postMethod=new PostMethod(this.mwApiUrl);
            postMethod.addParameter("action", "login");
            postMethod.addParameter("lgname", this.mwLogin);
            postMethod.addParameter("lgpassword",  this.mwPassword);
            postMethod.addParameter("format", "xml");
            if(mwToken!=null)
                {
                LOG.info("using mwToken "+mwToken);
                postMethod.addParameter("lgtoken",mwToken);
                }
            this.client.executeMethod(postMethod);
            InputStream in=postMethod.getResponseBodyAsStream();
            Document dom= this.domBuilder.parse(in);
            postMethod.releaseConnection();
            in.close();
            
            mwToken = this.xpath.evaluate("/api/login/@token",dom);
            String result= this.xpath.evaluate("/api/login/@result",dom);
            if(result==null) result="";
            LOG.info("server says "+result);
            if(result.equals("Success"))
            	{
            	break;
            	}
            else if(result.equals("NeedToken"))
            	{
            	continue;
            	}
            else
                {
                throw new RuntimeException("Cannot log as "+
                		this.mwLogin+
                		" result:"+result
                		);
                }
            }
        }

    /** log off from mediawiki */
    private void logout() throws IOException,SAXException
        {
        PostMethod postMethod=new PostMethod(this.mwApiUrl);
        postMethod.addParameter("action", "logout");
        postMethod.addParameter("format", "xml");
        this.client.executeMethod(postMethod);
        InputStream in=postMethod.getResponseBodyAsStream();
        this.domBuilder.parse(in);
        in.close();
        postMethod.releaseConnection();
        LOG.info("logged out");
        }

    /** parse Gene doc */
    private void parseDoc(
            XMLEventReader reader,
            Document dom,
            Element root)
        throws Exception
        {
        while(reader.hasNext())
            {
            XMLEvent evt=reader.nextEvent();
            if(evt.isEndElement())
                {
                return;
                }
            else if(evt.isStartElement())
                {
                Element node=dom.createElement(evt.asStartElement().getName().getLocalPart());
                root.appendChild(node);
                Iterator<?> iter=evt.asStartElement().getAttributes();
                while(iter.hasNext())
                    {
                    Attribute att=(Attribute)iter.next();
                    node.setAttribute(att.getName().getLocalPart(), att.getValue());
                    }
                parseDoc(reader,dom,node);
                }
            else if(evt.isCharacters())
                {
                root.appendChild(dom.createTextNode(evt.asCharacters().getData()));
                }
            }
        }

    
    /** let's do the job */
    @SuppressWarnings("resource")
	private void run(InputStream in)
        throws Exception
        {
        login();

        
        TupleBinding<SortedSet<Locus>> aliasesBinding=new TupleBinding<SortedSet<Locus>>()
        	{
        	@Override
        	public SortedSet<Locus> entryToObject(TupleInput in)
        		{
        		int n=in.readInt();
        		SortedSet<Locus> set=new TreeSet<LocalGeneWiki.Locus>();
        		for(int i=0;i< n;++i)
        			{
        			Locus locus=new Locus();
        			locus.locus=in.readString();
        			locus.description=in.readString();
        			set.add(locus);
        			}
        		return set;
        		}
        	
        	@Override
    		public void objectToEntry(SortedSet<Locus> set, TupleOutput out)
        		{    			
        		out.writeInt(set.size());
        		for(Locus locus:set)
        			{
        			out.writeString(locus.locus);
        			out.writeString(locus.description==null?"":locus.description);
        			}
    			}
        	
        	};
        
        // create XSLT factory and et XSLT stylesheet
        InputStream xslIn=null;
        if(this.altXsltStylesheet!=null)
        	{
        	LOG.info("reading alt stylesheet "+this.altXsltStylesheet);
        	xslIn=new FileInputStream(this.altXsltStylesheet);
        	}
        else
        	{
        	LOG.info("reading /META-INF/gene2wiki.xsl");
        	xslIn=LocalGeneWiki.class.getResourceAsStream("/META-INF/gene2wiki.xsl");
        	}
        if(xslIn==null) throw new IOException("cannot get xsl");
        LOG.info("read xsl ok");

        TransformerFactory trFactory=TransformerFactory.newInstance();
        Templates templates=trFactory.newTemplates(new StreamSource(xslIn));
        xslIn.close();
        Transformer transform=templates.newTransformer();
        transform.setParameter("templatePrefix",this.templatePrefix);
        transform.setParameter("ns",this.articleNamespace);

        //create xml stream factory
        

      
        //create XPATH expressions
        LOG.info("compile xpath");
        XPathExpression idExpr=xpath.compile("./Entrezgene_track-info/Gene-track/Gene-track_geneid");
        XPathExpression locusExpr=xpath.compile("./Entrezgene_gene/Gene-ref/Gene-ref_locus");
        XPathExpression descExpr=xpath.compile("./Entrezgene_gene/Gene-ref/Gene-ref_desc");
        //XPathExpression refGeneExpr=xpath.compile("./Entrezgene_locus/Gene-commentary/Gene-commentary_products/Gene-commentary[Gene-commentary_heading='Reference']/Gene-commentary_accession");
        //XPathExpression ensemblExpr=xpath.compile("./Entrezgene_gene/Gene-ref/Gene-ref_db/Dbtag[Dbtag_db='Ensembl']/Dbtag_tag/Object-id/Object-id_str");
        XPathExpression synonyms1=xpath.compile("./Entrezgene_gene/Gene-ref/Gene-ref_syn/Gene-ref_syn_E");
        XPathExpression synonyms2=xpath.compile("./Entrezgene_prot/Prot-ref/Prot-ref_name/Prot-ref_name_E");

        //call NCBI gene2xml

        
        XMLEventReader reader= xmlInputFactory.createXMLEventReader(in);
        while(reader.hasNext())
            {
            XMLEvent evt=reader.nextEvent();
            if(!evt.isStartElement()) continue;
            //it is a gene
            if(!evt.asStartElement().getName().getLocalPart().equals("Entrezgene")) continue;
            Document dom=this.domBuilder.newDocument();
            
            Element geneElement=dom.createElement("Entrezgene");
           
            
            //get the whole record as DOM
            parseDoc(reader,dom,geneElement);
            
            StringWriter articleContent=new StringWriter();
            transform.transform(
            		new DOMSource(geneElement),
            		new StreamResult(articleContent)
            		);
           
            
            //get locus name with xpath
            String geneid=(String)idExpr.evaluate(geneElement, XPathConstants.STRING);
            String locus=(String)locusExpr.evaluate(geneElement, XPathConstants.STRING);
           // String ensembl=(String)ensemblExpr.evaluate(geneElement, XPathConstants.STRING);
           // String refGene=(String)refGeneExpr.evaluate(geneElement, XPathConstants.STRING);
            String descr=(String)descExpr.evaluate(geneElement, XPathConstants.STRING);

            
            
            
            LOG.info(
        		"ID:"+geneid+
        		"\t"+locus+
        		"\t"+descExpr.evaluate(geneElement, XPathConstants.STRING)
        		);
            StringWriter sw=new StringWriter();
            transform.transform(new DOMSource(geneElement),
            		new StreamResult(sw)
            		);
            
            //cleanup
            System.gc();
            
            
            LOG.info("Creating Template: for gene ID."+geneid+" / "+locus);
            PostMethod postMethod=new PostMethod(this.mwApiUrl);
            postMethod.addParameter("action", "query");
            postMethod.addParameter("intoken", "edit");
            postMethod.addParameter("titles", "Template:"+this.templatePrefix+geneid);
            postMethod.addParameter("prop", "info");
            postMethod.addParameter("format", "xml");


            this.client.executeMethod(postMethod);
            Page page=parsePage(postMethod);
            postMethod.releaseConnection();

            this.postNewArticle(
            		"Template:"+this.templatePrefix+geneid,
            		"bot creating template for Gene "+locus,
            		page.edittoken,
            		page.starttimestamp,
            		sw.toString()
            		);
        
            if(this.createArticle)
		       	 {
		       	 //check article doesn't exists
		       	 postMethod=new PostMethod(this.mwApiUrl);
		            postMethod.addParameter("action", "query");
		            postMethod.addParameter("intoken", "edit");
		            postMethod.addParameter("titles",localizedArticle(locus));
		            postMethod.addParameter("prop", "info");
		            postMethod.addParameter("format", "xml");
		            this.client.executeMethod(postMethod);
		            page  = parsePage(postMethod);
		            postMethod.releaseConnection();

		            if(page.missing)
		           	 {		           	 
		           	 //ok, page does not exist, create it
		           	  String article="{{"+this.templatePrefix+geneid+"}}";
		           	 
		              this.postNewArticle(
		            		  	localizedArticle(locus),
		                		"bot creating article for Gene "+locus,
		                		page.edittoken,
		                		page.starttimestamp,
		                		article
		                		);
		           	 }
		       	 }
            if(createAliases)
	            {
	            Set<String> aliases=new HashSet<String>();
	            for(XPathExpression expr:new XPathExpression[]{synonyms1,synonyms2})
		            {
		            NodeList alias=(NodeList)expr.evaluate(geneElement,XPathConstants.NODESET);
		            for(int i=0;i< alias.getLength();++i)
		            	{
		            	aliases.add(alias.item(i).getTextContent().trim());
		            	}
		            }
	            aliases.remove("");
	            for(String alias: aliases)
		            {
		            DatabaseEntry keyEntry=new DatabaseEntry();
		            StringBinding.stringToEntry(alias, keyEntry);
		            DatabaseEntry dataEntry=new DatabaseEntry();
		            SortedSet<Locus> list=null;
		            if(aliasTopage.get(txn, keyEntry, dataEntry, LockMode.DEFAULT)==OperationStatus.SUCCESS)
		            	{
		            	list=aliasesBinding.entryToObject(dataEntry);
		            	}
		            else
		            	{
		            	list=new TreeSet<Locus>();
		            	}
		            Locus newlocus=new Locus();
		            newlocus.locus=locus;
		            newlocus.description=descr;
		            list.add(newlocus);
	
		            aliasesBinding.objectToEntry(list, dataEntry);
		            if(aliasTopage.put(txn, keyEntry, dataEntry)!=OperationStatus.SUCCESS)
		            	{
		            	throw new RuntimeException("Cannot insert alias");
		            	}
		            }
	            }
        	}
        
        reader.close();
        
        if(createAliases)
	        {
	        //insert aliases
	        DatabaseEntry keyEntry=new DatabaseEntry();
	        DatabaseEntry dataEntry=new DatabaseEntry();
	        Cursor cursor=aliasTopage.openCursor(txn, null);
	        while(cursor.getNext(keyEntry, dataEntry, LockMode.DEFAULT)==OperationStatus.SUCCESS)
	        	{
	        	String article=StringBinding.entryToString(keyEntry);
	        	LOG.info("Creating alias: "+article);
	        	SortedSet<Locus> list=aliasesBinding.entryToObject(dataEntry);
	        	cursor.delete();
	        	
	        	
	        	PostMethod postMethod=new PostMethod(this.mwApiUrl);
	            postMethod.addParameter("action", "query");
	            postMethod.addParameter("intoken", "edit");
	            postMethod.addParameter("titles", localizedArticle(article));
	            postMethod.addParameter("prop", "info");
	            postMethod.addParameter("format", "xml");
	            this.client.executeMethod(postMethod);
	            Page page  = parsePage(postMethod);
	            postMethod.releaseConnection();
		        String pageContent=null;
		        
		        
		        if(page.missing)
			        {
		        	if(list.isEmpty()) continue;
		        	if(list.size()==1)
		        		{
		        		LOG.info(article+" is a redirect");
		        		pageContent="#REDIRECT [["+ list.iterator().next().locus+"]]\n";
		        		}
		        	else
		        		{
		        		LOG.info(article+" is a disambiguation");
		        		StringBuilder sw=new StringBuilder();
		        		sw.append("'''").append(article).append("''' may refer to:\n");
		        		for(Locus L:list)
		        			{
		        			sw.append("* [[").append(L.locus).append("]] ");
		        			sw.append(L.description);
		        			sw.append("\n");
		        			}
		        		sw.append("\n{{disambiguation}}\n");
		        		pageContent=sw.toString();
		        		}
		        	
		        	 this.postNewArticle(
		             		localizedArticle(article),
		             		"bot creating article for Alias "+article,
		             		page.edittoken,
		             		page.starttimestamp,
		             		pageContent
		             		);
			        }
	        	}
	        cursor.close();
	        }
        
        logout();
        }
        
    
    /** post new article to mediawiki */
    private void postNewArticle(
    	String title,
    	String summary,
    	String editToken,
    	String starttimestamp,
    	String text
    	) throws IOException,SAXException,XPathExpressionException
    	{
    	PostMethod postMethod=new PostMethod(this.mwApiUrl);
        postMethod.addParameter("action", "edit");
        postMethod.addParameter("format", "xml");
        postMethod.addParameter("title", title);
        postMethod.addParameter("summary", summary);
        postMethod.addParameter("text", text);
        postMethod.addParameter("bot", "true");
        postMethod.addParameter("token", editToken);
        postMethod.addParameter("starttimestamp", starttimestamp);
        postMethod.addParameter("md5",DigestUtils.md5Hex(text));
        this.client.executeMethod(postMethod);
        InputStream inMW=postMethod.getResponseBodyAsStream();
        Document dom = this.domBuilder.parse(inMW);
        inMW.close();
        postMethod.releaseConnection();
        String result= this.xpath.evaluate("/api/edit/@result", dom);
        if(result==null) result="";
        if(!result.equals("Success"))
        	{
        	throw new IOException("Inserting "+title+" failed . result was "+result);
        	}
    	}
    
    
    	
    private void printUsage(PrintStream out)
		{
		out.println("Pierre Lindenbaum PhD; 2014");
		out.println("Options:");
		out.println(" -h help; This screen.");
		out.println(" -u <user.login>");
		out.println(" -p <user.password> (or asked later on command line)");
		out.println(" -a <api.url> e.g. http://en.wikipedia.org/w/api.php");
	
		out.println(" -c create article if it doesn't exist default: "+this.createArticle );
		out.println(" -r create alias (disambigation/redirect) if it doesn't exist default: "+this.createAliases );
	
		out.println(" -y <file> reads an alternat xslt stylesheet. (optional)");
		out.println(" -debug turns log off. (optional)");
		out.println(" -T <dir> tmp directory. (optional)");
	    	}
    
    /** main */
    private int mainInstance(String[] args) {
		try
			{
			File tmpDir=new File(System.getProperty("java.io.tmpdir"));
			LOG.setLevel(Level.ALL);
			int optind=0;
			while(optind< args.length)
				{
				if(args[optind].equals("-h") ||
				   args[optind].equals("-help") ||
				   args[optind].equals("--help"))
					{
					printUsage(System.err);
					return 0;
					}
				else if(args[optind].equals("-debug"))
					{
					LOG.setLevel(Level.OFF);
					}
				else if(args[optind].equals("-T") && optind+1< args.length)
					{
					tmpDir=new File(args[++optind]);
					}
				else if(args[optind].equals("-u") && optind+1< args.length)
					{
					this.mwLogin = args[++optind];
					}
				else if(args[optind].equals("-p") && optind+1< args.length)
					{
					this.mwPassword = args[++optind];
					}
				else if(args[optind].equals("-a") && optind+1< args.length)
					{
					this.mwApiUrl = args[++optind];
					}
				
				else if(args[optind].equals("-ns") && optind+1< args.length)
					{
					this.articleNamespace=args[++optind];
					}
				else if(args[optind].equals("-t") && optind+1< args.length)
					{
					this.templatePrefix=args[++optind];
					}
				else if(args[optind].equals("-c"))
					{
					this.createArticle=true;
					}
				else if(args[optind].equals("-r"))
					{
					this.createAliases=true;
					}
				else if(args[optind].equals("-y") && optind+1< args.length)
					{
					this.altXsltStylesheet=new File(args[++optind]);
					}
				else if(args[optind].equals("--"))
					{
					optind++;
					break;
					}
				else if(args[optind].startsWith("-"))
					{
					System.err.println("Unknown option "+args[optind]);
					return -1;
					}
				else 
					{
					break;
					}
				++optind;
				}
			
			
			if(this.mwLogin==null || this.mwLogin.isEmpty())
				{
				System.err.println("empty login.");
				return -1;
				}
			
			if(this.mwPassword==null)
                		{
				Console console=System.console();
				if(console==null)
				        {
				        System.err.println("Undefined Password.");
				        return -1;
				        }
				 char pass[] = console.readPassword("Mediawiki Password ? : ");
				 if(pass==null || pass.length==0)
				        {
				        System.err.println("Cannot read Password.");
				        return -1;
				        }
				this.mwPassword=new String(pass);
				}
			LOG.info("tmp dir is "+tmpDir);
			Transaction txn=null;
			EnvironmentConfig envCfg=new EnvironmentConfig();
			envCfg.setAllowCreate(true);
			envCfg.setReadOnly(false);
			
			this.environment=new Environment(tmpDir, envCfg);
			DatabaseConfig cfg=new DatabaseConfig();
			cfg.setAllowCreate(true);
			cfg.setReadOnly(false);
			this.aliasTopage=this.environment.openDatabase(txn, "alias2page", cfg); 
			
			if(optind==args.length)
				{
				LOG.info("Reading from stdin");
				this.run(System.in);
				}
			else if(optind+1==args.length)
				{
				String filename=args[optind];
				LOG.info("Reading from "+filename);
				FileInputStream in=new FileInputStream(filename);
				this.run(in);
				in.close();
				}
			else	
				{
				System.err.println("Illegal number of arguments.");
				return -1;
				}
			
			
			LOG.info("Done.");
			return 0;
			} 
		catch(Throwable err)
			{
			err.printStackTrace();
			return -1;
			}
		finally
			{
			if(this.aliasTopage!=null)
				{
				this.aliasTopage.close();
				}
			if(environment!=null)
				{
				environment.close();
				}
			}
		}
		
	public static void main(String[] args)
		{
		int ret=new LocalGeneWiki().mainInstance(args);
		System.exit(ret);
		}
    }
