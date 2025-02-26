package com.flower.net.socks5s;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.flower.net.config.serverconf.ServerConfig;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    final static Logger LOGGER = LoggerFactory.getLogger(Main.class);

    static final String CONFIG_OPTION_NAME = "config";
    static final String APP_NAME = "Sock5s server";

    public static void main(String[] args) {
        // create the command line parser
        CommandLineParser parser = new DefaultParser();

        // create the Options
        Options options = new Options();
        options.addOption("c", CONFIG_OPTION_NAME, true, "Config file name");

        try {
            // Parse the command line arguments
            CommandLine line = parser.parse(options, args);

            if (line.hasOption(CONFIG_OPTION_NAME)) {
                String configName = line.getOptionValue(CONFIG_OPTION_NAME);
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                        .registerModule(new GuavaModule());

                ServerConfig serverConfig;
                File configFile = new File(configName);
                if (configFile.exists()) {
                    serverConfig = mapper.readValue(configFile, ServerConfig.class);
                } else {
                    String resourceStr = Resources.toString(Resources.getResource(configName), Charsets.UTF_8);
                    serverConfig = mapper.readValue(resourceStr, ServerConfig.class);
                }

                Socks5sServer.run(serverConfig);
            } else {
                System.out.println("Please specify config name");

                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(APP_NAME, options);
            }
        } catch (ParseException pe) {
            System.out.println("Argument parsing error: " + pe.getMessage());

            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(APP_NAME, options);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
