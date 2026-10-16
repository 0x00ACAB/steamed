package xyz.kkaii.steamed.net

import xyz.kkaii.steamed.lib.Option
import org.bitlet.weupnp.GatewayDiscover
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.SAXException
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException


class UPnPublisher : IServerPublisher {
    val logger: Logger = LoggerFactory.getLogger("UPnP")

    @Throws(IOException::class, ParserConfigurationException::class, SAXException::class)
    override fun publish(port: Int): Option<String?> {
        val discover = GatewayDiscover()
        logger.info("Looking for Gateway Devices")
        discover.discover()

        val d = discover.getValidGateway()
        if (null != d) {
            logger.info(
                "Found gateway device.\n{} ({})",
                d.modelName, d.modelDescription
            )
        } else {
            logger.error("No valid gateway device found.")
            return Option.none<String?>()
        }

        val localAddress = d.localAddress
        logger.info("Using local address: {}", localAddress)
        val externalIPAddress = d.getExternalIPAddress()
        logger.info("External address: {}", externalIPAddress)

        logger.info("Attempting to map port {}", port)
        if (!d.addPortMapping(
                port, port,
                localAddress.hostAddress, "TCP", "Minecraft steam p2p"
            )
        ) {
            logger.error("Port mapping attempt failed, external connections likely won't reach this host")
            return Option.none<String?>()
        }

        return Option.some<String?>(d.getExternalIPAddress())
    }
}
