package xyz.kkaii.steamed.net

import xyz.kkaii.steamed.lib.Option
import org.xml.sax.SAXException
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

interface IServerPublisher {
    // Publish the server on a given port, returns the Ip address
    @Throws(IOException::class, ParserConfigurationException::class, SAXException::class)
    fun publish(port: Int): Option<String?>?
}
