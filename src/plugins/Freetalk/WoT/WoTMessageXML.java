package plugins.Freetalk.WoT;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.Message.MessageID;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.keys.FreenetURI;

/**
 * Generator & parsers of message XML. Compatible to the FMS message XML format.
 */
public final class WoTMessageXML {
	
	public static final int MAX_XML_SIZE = 128 * 1024;
	
	public static final int MAX_LISTED_PARENT_MESSAGES = 32;
	
	private static final int XML_FORMAT_VERSION = 1;
	
	private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd");
	private final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm:ss");
	
	
	private final DocumentBuilder mDocumentBuilder;
	
	private final DOMImplementation mDOM;
	
	private final Transformer mSerializer;
	
	public WoTMessageXML() {
		try {
			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			xmlFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			// DOM parser uses .setAttribute() to pass to underlying Xerces
			xmlFactory.setAttribute("http://apache.org/xml/features/disallow-doctype-decl", true);
			mDocumentBuilder = xmlFactory.newDocumentBuilder(); 
			mDOM = mDocumentBuilder.getDOMImplementation();

			mSerializer = TransformerFactory.newInstance().newTransformer();
			mSerializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			mSerializer.setOutputProperty(OutputKeys.INDENT, "no");
			mSerializer.setOutputProperty(OutputKeys.STANDALONE, "no");
		}
		catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	
	public void encode(Message m, OutputStream os) throws TransformerException, ParserConfigurationException {
		synchronized(m) {
			Document xmlDoc;
			synchronized(mDocumentBuilder) {
				xmlDoc = mDOM.createDocument(null, Freetalk.PLUGIN_TITLE, null);
			}
			
			Element rootElement = xmlDoc.getDocumentElement();

			Element messageTag = xmlDoc.createElement("Message");
			messageTag.setAttribute("version", Integer.toString(XML_FORMAT_VERSION)); /* Version of the XML format */
			
			Element idTag = xmlDoc.createElement("MessageID");
			idTag.appendChild(xmlDoc.createCDATASection(m.getID()));
			messageTag.appendChild(idTag);

			Element subjectTag = xmlDoc.createElement("Subject");
			subjectTag.appendChild(xmlDoc.createCDATASection(m.getTitle()));
			messageTag.appendChild(subjectTag);
			
			Element dateTag = xmlDoc.createElement("Date");
			synchronized(mDateFormat) {
				dateTag.appendChild(xmlDoc.createTextNode(mDateFormat.format(m.getDate())));
			}
			messageTag.appendChild(dateTag);
			
			Element timeTag = xmlDoc.createElement("Time");
			synchronized(mTimeFormat) {
				timeTag.appendChild(xmlDoc.createTextNode(mTimeFormat.format(m.getDate())));
			}
			messageTag.appendChild(timeTag);
			
			Element boardsTag = xmlDoc.createElement("Boards");
			for(Board b : m.getBoards()) {
				Element boardTag = xmlDoc.createElement("Board");
				boardTag.appendChild(xmlDoc.createCDATASection(b.getName()));
				boardsTag.appendChild(boardTag);
			}
			messageTag.appendChild(boardsTag);
			
			try {
				final Board replyToBoard = m.getReplyToBoard();
				Element replyBoardTag = xmlDoc.createElement("ReplyBoard");
				replyBoardTag.appendChild(xmlDoc.createCDATASection(replyToBoard.getName()));
				messageTag.appendChild(replyBoardTag);
			} catch(NoSuchBoardException e) {}

			if(!m.isThread()) {
				Element inReplyToTag = xmlDoc.createElement("InReplyTo");
				try {
					Element inReplyToMessage = xmlDoc.createElement("Message");
						Element inReplyToOrder = xmlDoc.createElement("Order"); inReplyToOrder.appendChild(xmlDoc.createTextNode("0"));	/* For FMS compatibility, not used by Freetalk */
						Element inReplyToID = xmlDoc.createElement("MessageID"); inReplyToID.appendChild(xmlDoc.createCDATASection(m.getParentID()));
						Element inReplyToURI = xmlDoc.createElement("MessageURI"); inReplyToURI.appendChild(xmlDoc.createCDATASection(m.getParentURI().toString()));
					inReplyToMessage.appendChild(inReplyToOrder);
					inReplyToMessage.appendChild(inReplyToID);
					inReplyToMessage.appendChild(inReplyToURI);
					inReplyToTag.appendChild(inReplyToMessage);
				}
				catch(NoSuchMessageException e) { }
					
				try {
					Element inReplyToThread = xmlDoc.createElement("Thread");
						Element inReplyToID = xmlDoc.createElement("MessageID"); inReplyToID.appendChild(xmlDoc.createCDATASection(m.getThreadID()));
						Element inReplyToURI = xmlDoc.createElement("MessageURI"); inReplyToURI.appendChild(xmlDoc.createCDATASection(m.getThreadURI().toString()));
					inReplyToThread.appendChild(inReplyToID);
					inReplyToThread.appendChild(inReplyToURI);
					inReplyToTag.appendChild(inReplyToThread);
				}
				catch(NoSuchMessageException e) { }
				
				messageTag.appendChild(inReplyToTag);
			}

			Element bodyTag = xmlDoc.createElement("Body");
			bodyTag.appendChild(xmlDoc.createCDATASection(m.getText()));
			messageTag.appendChild(bodyTag);
			
			Attachment[] attachments = m.getAttachments();
			if(attachments != null) {
				Element attachmentsTag = xmlDoc.createElement("Attachments");
				for(Attachment a : attachments) {
					Element fileTag = xmlDoc.createElement("File"); 
						Element keyTag = xmlDoc.createElement("URI"); keyTag.appendChild(xmlDoc.createCDATASection(a.getURI().toString()));
						Element sizeTag = xmlDoc.createElement("Size"); sizeTag.appendChild(xmlDoc.createCDATASection(Long.toString(a.getSize())));
					fileTag.appendChild(keyTag);
					fileTag.appendChild(sizeTag);
					attachmentsTag.appendChild(fileTag);
				}
				messageTag.appendChild(attachmentsTag);
			}

			rootElement.appendChild(messageTag);

			DOMSource domSource = new DOMSource(xmlDoc);
			StreamResult resultStream = new StreamResult(os);
			synchronized(mSerializer) {
				mSerializer.transform(domSource, resultStream);
			}
		}
	}
	
	/**
	 * 
	 * @param db
	 * @param inputStream
	 * @param messageManager Needed for retrieving the Board object from the Strings of the board names.
	 * @param messageList
	 * @param uri
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("deprecation")
	public Message decode(MessageManager messageManager, InputStream inputStream, WoTMessageList messageList, FreenetURI uri) throws Exception {
		if(inputStream.available() > MAX_XML_SIZE)
			throw new IllegalArgumentException("XML contains too many bytes: " + inputStream.available());
		
		final Document xml;
		synchronized(mDocumentBuilder) {
			xml = mDocumentBuilder.parse(inputStream);
		}
		
		final Element messageElement = (Element)xml.getElementsByTagName("Message").item(0);
		
		if(Integer.parseInt(messageElement.getAttribute("version")) > XML_FORMAT_VERSION)
			throw new Exception("Version " + messageElement.getAttribute("version") + " > " + XML_FORMAT_VERSION);
		
		final MessageID messageID = MessageID.construct(messageElement.getElementsByTagName("MessageID").item(0).getTextContent());
		messageID.throwIfAuthorDoesNotMatch(messageList.getAuthor()); // Double check, the message constructor should also do this.
		
		final String messageTitle = messageElement.getElementsByTagName("Subject").item(0).getTextContent();

		final Date messageDate;
		synchronized(mDateFormat) {
			messageDate = mDateFormat.parse(messageElement.getElementsByTagName("Date").item(0).getTextContent());
		}
		final Date messageTime;
		synchronized(mTimeFormat) {
			messageTime= mTimeFormat.parse(messageElement.getElementsByTagName("Time").item(0).getTextContent());
		}
		messageDate.setHours(messageTime.getHours()); messageDate.setMinutes(messageTime.getMinutes()); messageDate.setSeconds(messageTime.getSeconds());
		
		final Set<Board> messageBoards = new HashSet<Board>();
		final Element boardsElement = (Element)messageElement.getElementsByTagName("Boards").item(0);
		final NodeList boardList = boardsElement.getElementsByTagName("Board");
		
		if(boardList.getLength() > Message.MAX_BOARDS_PER_MESSAGE)
			throw new IllegalArgumentException("Too many boards: " + boardList.getLength());
		
		for(int i = 0; i < boardList.getLength(); ++i)
			messageBoards.add(messageManager.getOrCreateBoard(boardList.item(i).getTextContent()));
		
		final Node replyToBoardElement = messageElement.getElementsByTagName("ReplyBoard").item(0);
		final Board messageReplyToBoard =  replyToBoardElement != null ? messageManager.getOrCreateBoard(replyToBoardElement.getTextContent()) :
												null; 
		
		WoTMessageURI parentMessageURI = null;
		WoTMessageURI parentThreadURI = null;
		
		final Element inReplyToElement = (Element)messageElement.getElementsByTagName("InReplyTo").item(0);
		if(inReplyToElement != null) {
			final NodeList parentMessages = inReplyToElement.getElementsByTagName("Message");
			
			// FIXME: We should get rid of the Order stuff, we are not FMS compatible anyway.
			// Listing the parent messages does not help us because we use CHK message URIs...
			
			if(parentMessages.getLength() > MAX_LISTED_PARENT_MESSAGES)
				throw new IllegalArgumentException("Too many parent messages listed in message: " + parentMessages.getLength());
			
			for(int i = 0; i < parentMessages.getLength(); ++i) {
				Element parentMessage = (Element)parentMessages.item(i);
				if(parentMessage.getElementsByTagName("Order").item(0).getTextContent().equals("0"))
					parentMessageURI = new WoTMessageURI(parentMessage.getElementsByTagName("MessageURI").item(0).getTextContent());
			}
		
			Element threadElement = (Element)inReplyToElement.getElementsByTagName("Thread").item(0);
			if(threadElement != null)
				parentThreadURI = new WoTMessageURI(threadElement.getElementsByTagName("MessageURI").item(0).getTextContent());
		}
		
		final String messageBody = messageElement.getElementsByTagName("Body").item(0).getTextContent();
		
		final Element attachmentsElement = (Element)messageElement.getElementsByTagName("Attachments").item(0);
		ArrayList<Message.Attachment> messageAttachments = null;
		if(attachmentsElement != null) {
			final NodeList fileElements = attachmentsElement.getElementsByTagName("File");
			
			if(fileElements.getLength() > Message.MAX_ATTACHMENTS_PER_MESSAGE)
				throw new IllegalArgumentException("Too many attachments listed in message: " + fileElements.getLength());
						
			messageAttachments = new ArrayList<Message.Attachment>(fileElements.getLength() + 1);
			
			for(int i = 0; i < fileElements.getLength(); ++i) {
				Element fileElement = (Element)fileElements.item(i);
				Node keyElement = fileElement.getElementsByTagName("URI").item(0);
				Node sizeElement = fileElement.getElementsByTagName("Size").item(0);
				messageAttachments.add(new Message.Attachment(	new FreenetURI(keyElement.getTextContent()),
																sizeElement != null ? Long.parseLong(sizeElement.getTextContent()) : -1));
			}
		}
		
		return WoTMessage.construct(messageList, uri, messageID, parentThreadURI, parentMessageURI, messageBoards, messageReplyToBoard,
									messageList.getAuthor(), messageTitle, messageDate, messageBody, messageAttachments);
	}
}
