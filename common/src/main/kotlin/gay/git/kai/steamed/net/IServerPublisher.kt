package gay.git.kai.steamed.net

import gay.git.kai.steamed.lib.Option
import org.xml.sax.SAXException
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

interface IServerPublisher {
    // Publish the server on a given port, returns the Ip address
    @Throws(IOException::class, ParserConfigurationException::class, SAXException::class)
    fun publish(port: Int): Option<String?>?
}
