/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import au.com.bytecode.opencsv.CSVReader;
import hudson.FilePath;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Test a CSV series.
 *
 * @author Allen Reese
 */
public class CSVReaderTest extends SeriesTestCase {
    private static final Logger LOGGER = Logger.getLogger(CSVReaderTest.class.getName());

    private static final String[] FILES = {
            "test.csv",
            "test_trailing_spaces.csv",
            "test_trailing_semicolon.csv"
    };
    private static final int[] LINES = {2, 3, 2};
    private static final int[] COLUMNS = {8, 3, 9};

    @Test
    public void testCSVReader() {
        for (int index = 0; index < FILES.length; index++) {
            CSVReader csvReader = null;
            InputStream inputStream = null;
            InputStreamReader inputReader = null;

            FilePath[] seriesFiles;
            try {
                seriesFiles = workspaceRootDir.list(FILES[index]);

                if (seriesFiles != null && seriesFiles.length < 1) {
                    fail("No plot data file found: "
                            + workspaceRootDir.getName() + " " + FILES[index]);
                }

                LOGGER.info("Loading plot series data from: " + FILES[index]);

                inputStream = seriesFiles[0].read();

                inputReader = new InputStreamReader(inputStream);
                csvReader = new CSVReader(inputReader);

                // save the header line to use it for the plot labels.
                String[] nextLine;
                // read each line of the CSV file and add to rawPlotData
                int lineNum = 0;
                while ((nextLine = csvReader.readNext()) != null) {
                    // for some reason csv reader returns an empty line sometimes.
                    if (nextLine.length == 1 && nextLine[0].length() == 0) {
                        break;
                    }

                    if (COLUMNS[index] != nextLine.length) {
                        StringBuilder msg = new StringBuilder();
                        msg.append("column count is not equal ").append(nextLine.length);
                        msg.append(" expected ").append(COLUMNS[index]).append(" at line ");
                        msg.append(lineNum).append(" line: ").append("'");
                        for (String s : nextLine) {
                            msg.append("\"").append(s).append("\":").append(s.length()).append(",");
                        }
                        msg.append("' length ").append(nextLine.length);
                        assertEquals(msg.toString(), COLUMNS[index], nextLine.length);
                    }
                    ++lineNum;
                }
                assertEquals("Line count is not equal " + lineNum + " expected " + LINES[index],
                        LINES[index], lineNum);
            } catch (IOException | InterruptedException e) {
                fail("Exception " + e);
            } finally {
                try {
                    if (csvReader != null) {
                        csvReader.close();
                    }
                } catch (IOException e) {
                    fail("Exception " + e);
                }
                IOUtils.closeQuietly(inputReader);
                IOUtils.closeQuietly(inputStream);
            }
        }
    }
}
