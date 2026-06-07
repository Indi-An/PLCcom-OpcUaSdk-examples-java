// MIT License
// Copyright (c) Indi.An GmbH

import java.awt.GraphicsEnvironment;
import java.util.Calendar;

import com.plccom.opc.ua.builtintypes.DateTime;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.builtintypes.UnsignedByte;
import com.plccom.opc.ua.builtintypes.UnsignedInteger;
import com.plccom.opc.ua.builtintypes.UnsignedShort;
import com.plccom.opc.ua.builtintypes.Variant;
import com.plccom.opc.ua.core.ConfigurationVersionDataType;
import com.plccom.opc.ua.core.DataSetMetaDataType;
import com.plccom.opc.ua.core.FieldMetaData;
import com.plccom.opc.ua.pubsub.PubSubPublisherId;
import com.plccom.opc.ua.pubsub.encoding.PubSubEncodingException;
import com.plccom.opc.ua.pubsub.encoding.json.JsonDataSetMessage;
import com.plccom.opc.ua.pubsub.encoding.json.JsonDataSetMessageContentMask;
import com.plccom.opc.ua.pubsub.encoding.json.JsonDataSetMetaDataNetworkMessage;
import com.plccom.opc.ua.pubsub.encoding.json.JsonNetworkMessage;
import com.plccom.opc.ua.pubsub.encoding.json.JsonNetworkMessageContentMask;
import com.plccom.opc.ua.pubsub.encoding.uadp.UadpDataSetMessage;
import com.plccom.opc.ua.pubsub.encoding.uadp.UadpNetworkMessage;
import com.plccom.opc.ua.pubsub.encoding.uadp.UadpNetworkMessage.UadpPayloadMessage;
import com.plccom.opc.ua.pubsub.sdk.UaPubSubDataReceivedEvent;
import com.plccom.opc.ua.pubsub.sdk.UaPubSubErrorEvent;
import com.plccom.opc.ua.pubsub.transport.mqtt.MqttBodyEncoding;
import com.plccom.opc.ua.pubsub.transport.mqtt.MqttProtocolVersion;

final class PubSubWorkshopHelper {

    private static boolean consoleOpened;

    private PubSubWorkshopHelper() {
    }

    static void openConsole(String title) {
        if (!isPlccomConsoleEnabled()) {
            return;
        }
        PLCcomConsole.open(title, 1000);
        consoleOpened = true;
    }


    static boolean isEnterPressed(java.io.BufferedReader reader) throws java.io.IOException {
        if (reader == null) {
            return false;
        }
        return reader.ready();
    }

    static void closeConsole() {
        if (!consoleOpened) {
            return;
        }
        PLCcomConsole.close();
        consoleOpened = false;
    }
    static void waitForEnterAndCloseConsole() {
        if (!consoleOpened) {
            return;
        }
        PLCcomConsole.waitForEnter();
        PLCcomConsole.close();
        consoleOpened = false;
    }

    static void printHint(String title, String text) {
        System.out.println("-- " + title + " " + repeat('-', Math.max(1, 56 - title.length())));
        for (String line : wrap(text, 74)) {
            System.out.println("   " + line);
        }
        System.out.println();
    }

    static void printPubSubError(UaPubSubErrorEvent event) {
        StringBuilder builder = new StringBuilder();
        builder.append("[PubSub ");
        builder.append(event.getSeverity());
        builder.append(" ");
        builder.append(event.getType());
        builder.append("] ");
        builder.append(event.getDetailedMessage());
        if (event.getDataSetName() != null) {
            builder.append(" DataSet=");
            builder.append(event.getDataSetName());
        }
        if (event.getEndpointUrl() != null) {
            builder.append(" Endpoint=");
            builder.append(event.getEndpointUrl());
        }
        System.out.println(builder.toString());
        Throwable exception = event.getException();
        if (isVerboseErrorOutputEnabled() && exception != null) {
            exception.printStackTrace(System.out);
        }
    }

    static String formatDecimalField(UaPubSubDataReceivedEvent event,
            String fieldName, String format) {
        Variant value = event.getField(fieldName);
        if (value == null) {
            return "<not present>";
        }
        Object rawValue = value.getValue();
        if (rawValue instanceof Number) {
            return String.format(format, ((Number) rawValue).doubleValue());
        }
        return String.valueOf(rawValue);
    }

    static String formatDiscoveredField(Variant value) {
        if (value == null || value.getValue() == null) {
            return "<null>";
        }
        Object rawValue = value.getValue();
        if (rawValue instanceof Number) {
            return String.format("%5.2f", ((Number) rawValue).doubleValue());
        }
        return String.valueOf(rawValue);
    }

    private static boolean isVerboseErrorOutputEnabled() {
        return isTrue(System.getProperty("plccom.pubsub.workshop.verboseErrors"))
                || isTrue(System.getenv("PLCCOM_PUBSUB_WORKSHOP_VERBOSE_ERRORS"));
    }

    private static boolean isPlccomConsoleEnabled() {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        String property = System.getProperty("plccom.pubsub.workshop.console");
        String environment = System.getenv("PLCCOM_PUBSUB_WORKSHOP_CONSOLE");
        return !isFalse(property) && !isFalse(environment);
    }

    private static boolean isTrue(String value) {
        return "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "yes".equalsIgnoreCase(value);
    }

    private static boolean isFalse(String value) {
        return "false".equalsIgnoreCase(value)
                || "0".equals(value)
                || "no".equalsIgnoreCase(value);
    }

    private static String[] wrap(String text, int width) {
        if (text == null || text.length() == 0) {
            return new String[] { "" };
        }
        java.util.List<String> lines = new java.util.ArrayList<String>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= width) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines.toArray(new String[lines.size()]);
    }

    private static String repeat(char character, int count) {
        StringBuilder builder = new StringBuilder(Math.max(0, count));
        for (int ii = 0; ii < count; ii++) {
            builder.append(character);
        }
        return builder.toString();
    }

    static MqttProtocolVersion parseMqttProtocolVersion(String[] args) {
        if (args == null) {
            return MqttProtocolVersion.AUTO;
        }
        for (String arg : args) {
            if ("auto".equalsIgnoreCase(arg)
                    || "mqtt-auto".equalsIgnoreCase(arg)) {
                return MqttProtocolVersion.AUTO;
            }
            if ("mqtt3".equalsIgnoreCase(arg)
                    || "mqtt-3.1.1".equalsIgnoreCase(arg)
                    || "mqtt311".equalsIgnoreCase(arg)) {
                return MqttProtocolVersion.MQTT_3_1_1;
            }
            if ("mqtt5".equalsIgnoreCase(arg)
                    || "mqtt-5.0".equalsIgnoreCase(arg)) {
                return MqttProtocolVersion.MQTT_5_0;
            }
        }
        return MqttProtocolVersion.AUTO;
    }

    static void printMqttProtocolVersion(MqttProtocolVersion protocolVersion) {
        System.out.println("MQTT protocol version: "
                + protocolVersion.getConfigurationValue());
        System.out.println();
    }

    static boolean hasArgument(String[] args, String value) {
        if (args == null || value == null) {
            return false;
        }
        for (String arg : args) {
            if (value.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    static MqttBodyEncoding parseJsonBodyEncoding(String[] args) {
        if (args == null) {
            return MqttBodyEncoding.JSON;
        }
        for (String arg : args) {
            if ("gzip".equalsIgnoreCase(arg)
                    || "json-gzip".equalsIgnoreCase(arg)
                    || "json_gzip".equalsIgnoreCase(arg)) {
                return MqttBodyEncoding.JSON_GZIP;
            }
        }
        return MqttBodyEncoding.JSON;
    }

    static void printJsonBodyEncoding(MqttBodyEncoding bodyEncoding) {
        System.out.println("MQTT JSON body encoding: "
                + bodyEncoding.getContentType());
        System.out.println();
    }

    static String jsonTopicPrefix(MqttBodyEncoding bodyEncoding) {
        if (bodyEncoding == MqttBodyEncoding.JSON_GZIP) {
            return "opcua-gzip";
        }
        return "opcua";
    }

    static void printJsonTopicPrefix(MqttBodyEncoding bodyEncoding) {
        System.out.println("MQTT JSON topic prefix: "
                + jsonTopicPrefix(bodyEncoding));
        System.out.println();
    }

    static UadpDataSetMessage createTemperatureDataSetMessage(
            int sequenceNumber, double temperatureCelsius)
            throws PubSubEncodingException {
        return UadpDataSetMessage.builder()
                .sequenceNumber(sequenceNumber)
                .addField(new Variant(UnsignedShort.valueOf(sequenceNumber)))
                .addField(new Variant(Double.valueOf(temperatureCelsius)))
                .addField(new Variant("Temperature-Celsius"))
                .build();
    }

    static UadpDataSetMessage createMotorDataSetMessage(int sequenceNumber,
            double speed, double current, double temperature)
            throws PubSubEncodingException {
        return UadpDataSetMessage.builder()
                .sequenceNumber(sequenceNumber)
                .addField(new Variant(Double.valueOf(speed)))
                .addField(new Variant(Double.valueOf(current)))
                .addField(new Variant(Double.valueOf(temperature)))
                .build();
    }

    static UadpNetworkMessage createTemperatureMessage(int sequenceNumber,
            double temperatureCelsius, PubSubPublisherId publisherId,
            int writerGroupId, int dataSetWriterId)
            throws PubSubEncodingException {
        return UadpNetworkMessage.builder()
                .publisherId(publisherId)
                .writerGroupId(writerGroupId)
                .sequenceNumber(sequenceNumber)
                .addPayloadMessage(dataSetWriterId,
                        createTemperatureDataSetMessage(sequenceNumber,
                                temperatureCelsius))
                .build();
    }

    static JsonDataSetMessage createJsonTemperatureDataSetMessage(
            int sequenceNumber, double temperatureCelsius) {
        return JsonDataSetMessage.builder()
                .addField("Sequence", new Variant(UnsignedShort.valueOf(sequenceNumber)))
                .addField("Temperature", new Variant(Double.valueOf(temperatureCelsius)))
                .addField("EngineeringUnits", new Variant("Temperature-Celsius"))
                .build();
    }

    static JsonNetworkMessage createEnergyNetworkMessage(int sequenceNumber,
            double voltage, double current, double power, double energy,
            PubSubPublisherId publisherId, int dataSetWriterId) {
        int networkMask = JsonNetworkMessageContentMask.NETWORK_MESSAGE_HEADER
                | JsonNetworkMessageContentMask.DATA_SET_MESSAGE_HEADER
                | JsonNetworkMessageContentMask.PUBLISHER_ID;
        int dataSetMask = JsonDataSetMessageContentMask.DATA_SET_WRITER_ID
                | JsonDataSetMessageContentMask.SEQUENCE_NUMBER
                | JsonDataSetMessageContentMask.TIMESTAMP
                | JsonDataSetMessageContentMask.STATUS;
        JsonDataSetMessage dataSetMessage = JsonDataSetMessage.builder()
                .dataSetMessageContentMask(dataSetMask)
                .dataSetWriterId(dataSetWriterId)
                .sequenceNumber(sequenceNumber)
                .timestamp(new DateTime())
                .status(StatusCode.GOOD.getValue().longValue())
                .addField("Voltage", new Variant(Double.valueOf(voltage)))
                .addField("Current", new Variant(Double.valueOf(current)))
                .addField("Power", new Variant(Double.valueOf(power)))
                .addField("Energy", new Variant(Double.valueOf(energy)))
                .build();
        return JsonNetworkMessage.builder()
                .networkMessageContentMask(networkMask)
                .messageId("energy-meter-" + sequenceNumber)
                .publisherId(publisherId)
                .addDataSetMessage(dataSetMessage)
                .build();
    }

    static JsonDataSetMetaDataNetworkMessage createJsonTemperatureMetaDataMessage(
            String publisherId, String writerGroupName, int dataSetWriterId,
            String dataSetWriterName) {
        return JsonDataSetMetaDataNetworkMessage.builder()
                .messageId("temperature-metadata")
                .publisherId(publisherId)
                .writerGroupName(writerGroupName)
                .dataSetWriterId(dataSetWriterId)
                .dataSetWriterName(dataSetWriterName)
                .timestamp(new DateTime(2026, Calendar.MAY, 29, 13, 30, 0, 0))
                .metaData(createJsonTemperatureMetaData())
                .build();
    }

    static JsonDataSetMetaDataNetworkMessage createEnergyMetaDataMessage(
            String publisherId, String writerGroupName, int dataSetWriterId,
            String dataSetWriterName, String dataSetName) {
        return JsonDataSetMetaDataNetworkMessage.builder()
                .messageId("energy-meter-metadata")
                .publisherId(publisherId)
                .writerGroupName(writerGroupName)
                .dataSetWriterId(dataSetWriterId)
                .dataSetWriterName(dataSetWriterName)
                .timestamp(new DateTime())
                .metaData(createEnergyMetaData(dataSetName))
                .build();
    }

    static DataSetMetaDataType createJsonTemperatureMetaData() {
        DataSetMetaDataType result = new DataSetMetaDataType();
        result.setName("JsonTemperatureDataSet");
        result.setFields(new FieldMetaData[0]);
        result.setConfigurationVersion(new ConfigurationVersionDataType(
                UnsignedInteger.valueOf(1), UnsignedInteger.valueOf(0)));
        return result;
    }

    static DataSetMetaDataType createEnergyMetaData(String dataSetName) {
        DataSetMetaDataType result = new DataSetMetaDataType();
        result.setName(dataSetName);
        result.setFields(new FieldMetaData[] {
                createDoubleField("Voltage"),
                createDoubleField("Current"),
                createDoubleField("Power"),
                createDoubleField("Energy") });
        result.setConfigurationVersion(new ConfigurationVersionDataType(
                UnsignedInteger.valueOf(1), UnsignedInteger.valueOf(0)));
        return result;
    }

    static void printDecodedMessage(UadpNetworkMessage message) {
        System.out.println("PublisherId: " + message.getPublisherId());
        System.out.println("Payload messages: " + message.getPayloadMessages().size());

        for (UadpPayloadMessage payload : message.getPayloadMessages()) {
            System.out.println("  DataSetWriterId: " + payload.getDataSetWriterId());
            System.out.println("  DataSet sequence: " + payload.getMessage().getSequenceNumber());
            int index = 0;
            for (Variant field : payload.getMessage().getFields()) {
                System.out.println("  Field[" + index++ + "]: " + field.getValue());
            }
        }
        System.out.println();
    }

    static void printDecodedJsonMessage(JsonNetworkMessage message) {
        System.out.println("PublisherId: " + message.getPublisherId());
        System.out.println("WriterGroupName: " + message.getWriterGroupName());
        System.out.println("DataSetMessages: " + message.getDataSetMessages().size());

        for (JsonDataSetMessage dataSetMessage : message.getDataSetMessages()) {
            System.out.println("  DataSetWriterId: " + dataSetMessage.getDataSetWriterId());
            System.out.println("  DataSetWriterName: " + dataSetMessage.getDataSetWriterName());
            for (JsonDataSetMessage.Field field : dataSetMessage.getPayloadFields()) {
                System.out.println("  " + field.getName() + ": " + field.getValue().getValue());
            }
        }
        System.out.println();
    }

    private static FieldMetaData createDoubleField(String name) {
        FieldMetaData field = new FieldMetaData();
        field.setName(name);
        field.setBuiltInType(UnsignedByte.valueOf(11));
        field.setValueRank(-1);
        field.setMaxStringLength(UnsignedInteger.valueOf(0));
        return field;
    }
}
