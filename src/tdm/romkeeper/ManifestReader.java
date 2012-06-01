package tdm.romkeeper;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

// For testing
import org.xml.sax.InputSource;
import java.io.StringReader;

class ManifestReader
{
    ManifestReader() {}

    public ArrayList<Rom> readManifest(File cacheDir) {
        File cacheFile = new File(cacheDir, "manifest.xml");
        ArrayList<Rom> roms = new ArrayList<Rom>();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = null;

            dom = db.parse(cacheFile);
            Log.i("ManifestReader", "preparing to traverse");
            Element root = dom.getDocumentElement();
            Log.i("ManifestReader", "root: " + root.getTagName());
            if (!root.getTagName().equals("romlist")) {
                throw new Exception("Document not a romlist");
            }
            NodeList nodes = root.getChildNodes();
            for (int i = 0; i < nodes.getLength(); ++i) {
                Node node = nodes.item(i);
                if (node instanceof Element) {
                    Element e = (Element)node;
                    if (e.getTagName().equals("rom")) {
                        Log.i("ManifestReader", "Found rom");
                        String name = e.getAttribute("name");
                        Rom r = new Rom(name);
                        r.setUrl(e.getAttribute("url"));
                        r.setFilename(e.getAttribute("filename"));
                        r.setSize(e.getAttribute("size"));
                        r.setDigest(e.getAttribute("digest"));
                        r.setBasis(e.getAttribute("basis"));
                        r.setDeltaUrl(e.getAttribute("delta-url"));
                        r.setDeltaFilename(e.getAttribute("delta-filename"));
                        r.setDeltaSize(e.getAttribute("delta-size"));
                        roms.add(r);
                    }
                }
            }
            Log.i("ManifestReader", "success: " + roms.size() + " roms");
        }
        catch (Exception e) {
            Log.e("ManifestReader", "readManifest caught exception: " + e.getMessage());
            e.printStackTrace();
        }

        return roms;
    }
}
