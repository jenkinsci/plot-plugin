/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import hudson.FilePath;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Logger;

import au.com.bytecode.opencsv.CSVReader;

/**
 * Test a CSV series.
 * 
 * @author Allen Reese
 *
 */
public class CSVReaderTest extends SeriesTestCase {
    private static transient final Logger LOGGER = Logger.getLogger(CSVReaderTest.class.getName());
    
    private static final String[] files = {
		"test.csv",
	};
	
	private static final int[] lines = {
		2,
	};

	private static final int[] columns = {
		8,
	};

	public void testCSVReader()
	{
		// first create a FilePath to load the test Properties file.
		File workspaceDirFile = new File ("target/test-classes/");
		FilePath workspaceRootDir = new FilePath (workspaceDirFile);
		
		LOGGER.info("workspace File path: " + workspaceDirFile.getAbsolutePath());
		LOGGER.info("workspace Dir path: " + workspaceRootDir.getName());
		
		CSVReader csvreader = null;
		InputStream in = null;
		InputStreamReader inputReader = null;

		FilePath[] seriesFiles = null;
        try {
			seriesFiles = workspaceRootDir.list(files[0]);

			if (seriesFiles != null && seriesFiles.length < 1) {
				LOGGER.info("No plot data file found: " + workspaceRootDir.getName() + " " + files[0]);
				assertFalse(true);
			}
			
			LOGGER.info("Loading plot series data from: " + files[0]);
				
			in = seriesFiles[0].read();

			inputReader = new InputStreamReader(in);
			csvreader = new CSVReader(inputReader);

			// save the header line to use it for the plot labels.
			String[] nextLine;
	       	// read each line of the CSV file and add to rawPlotData
            int lineNum=0;
            while ((nextLine = csvreader.readNext()) != null)
            {
            	// for some reason csvreader returns an empty line sometimes.
            	if (nextLine.length==1 && nextLine[0].length()==0)
            		break;
            	
            	if (columns[0]!=nextLine.length)
            	{
            		StringBuilder msg=new StringBuilder();
            		msg.append("column count is not equal ").append(nextLine.length);
            		msg.append(" expected ").append(columns[0]).append(" at line ");
            		msg.append(lineNum).append(" line: ").append("'");
            		for (String s : nextLine)
            		{
            			msg.append("\"").append(s).append("\":").append(s.length()).append(",");
            		}
            		msg.append("' length ").append(nextLine.length);
            		assertTrue(msg.toString(),columns[0]==nextLine.length);
            	}
            	++lineNum;
            }
        	assertTrue("Line count is not equal " + lineNum + " expected " + lines[0],lines[0]==lineNum);
        } catch (IOException e) {
			assertFalse("Exception "+ e,true);
		} catch (InterruptedException e) {
			assertFalse("Exception "+ e,true);
		} finally {
			try {
	        	if (csvreader != null)
	        		csvreader.close();
			} catch (IOException e) {
			}
			
			try {
	        	if (inputReader != null)
	        		inputReader.close();
			} catch (IOException e) {
			}

			try {
	        	if (in != null)
	        		in.close();
			} catch (IOException e) {
			}
		}
	}
}
