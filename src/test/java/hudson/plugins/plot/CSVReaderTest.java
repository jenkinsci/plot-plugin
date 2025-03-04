/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import static hudson.plugins.plot.SeriesTestUtils.WORKSPACE_ROOT_DIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.opencsv.CSVReader;
import hudson.FilePath;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Test a CSV series.
 *
 * @author Allen Reese
 */
class CSVReaderTest {
    private static final Logger LOGGER = Logger.getLogger(CSVReaderTest.class.getName());

    private static final String[] FILES = {"test.csv", "test_trailing_spaces.csv", "test_trailing_semicolon.csv"};
    private static final int[] LINES = {2, 3, 2};
    private static final int[] COLUMNS = {8, 3, 9};

    @Test
    void testCSVReader() throws Exception {
        for (int index = 0; index < FILES.length; index++) {

            FilePath[] seriesFiles = WORKSPACE_ROOT_DIR.list(FILES[index]);
            assertFalse(
                    seriesFiles.length < 1,
                    "No plot data file found: " + WORKSPACE_ROOT_DIR.getName() + " " + FILES[index]);

            LOGGER.info("Loading plot series data from: " + FILES[index]);
            try (InputStream inputStream = seriesFiles[0].read();
                    InputStreamReader inputReader = new InputStreamReader(inputStream);
                    CSVReader csvReader = new CSVReader(inputReader)) {

                // save the header line to use it for the plot labels.
                String[] nextLine;
                // read each line of the CSV file and add to rawPlotData
                int lineNum = 0;
                while ((nextLine = csvReader.readNext()) != null) {
                    // for some reason csv reader returns an empty line sometimes.
                    if (nextLine.length == 1 && nextLine[0].isEmpty()) {
                        break;
                    }

                    if (COLUMNS[index] != nextLine.length) {
                        StringBuilder msg = new StringBuilder();
                        msg.append("column count is not equal ").append(nextLine.length);
                        msg.append(" expected ").append(COLUMNS[index]).append(" at line ");
                        msg.append(lineNum).append(" line: ").append("'");
                        for (String s : nextLine) {
                            msg.append("\"")
                                    .append(s)
                                    .append("\":")
                                    .append(s.length())
                                    .append(",");
                        }
                        msg.append("' length ").append(nextLine.length);
                        assertEquals(COLUMNS[index], nextLine.length, msg.toString());
                    }
                    ++lineNum;
                }
                assertEquals(LINES[index], lineNum, "Line count is not equal " + lineNum + " expected " + LINES[index]);
            }
        }
    }
}
