/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.  
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.AbstractDataset;

/**
 * A {@link CategoryDataset} implementation that stores numeric data points
 * and corresponding URLs.  This data structure is basically a table
 * with row and column names (keys).
 * 
 * @author Nigel Daley
 */
public class PlotCategoryDataset extends AbstractDataset implements CategoryDataset {
	private static final Logger LOGGER = Logger.getLogger(PlotCategoryDataset.class.getName());
	
	class DataElement {
		public Number number;
		public String url;
		public DataElement(Number n, String u) {
			this.number = n; 
			this.url = u; 
		}
	}
	
	/** The row keys */
	private ArrayList<Comparable> rowKeys;

	/** The column keys */
	private ArrayList<Comparable> columnKeys;

	/** The row data */
	private ArrayList<HashMap<Comparable,DataElement>> data;

	/** The max number of builds to plot */
	private int maxColumns;
	
	/**
	 * Creates a new empty instance.
	 * 

	 */
	public PlotCategoryDataset() {
		this.rowKeys = new ArrayList<Comparable>();
		this.columnKeys = new ArrayList<Comparable>();
		this.data = new ArrayList<HashMap<Comparable,DataElement>>();
	}

	/**
	 * Truncates the dataset to the <i>last</i> <code>maxColumns</code>
	 * columns.
	 * 
	 * @param maxColumns the maximum number columns that will appear to
	 *        be in the dataset.
	 */
	public void clipDataset(int maxColumns) {
		this.maxColumns = maxColumns;
		// Columns are lazily truncated when the data is queried.
		// Rows that contain no data when the columns are truncated are
		// removed here so that they don't show up in plot legends.
		if (getColumnCount() > 0) {
			Comparable lowColumn = getColumnKey(0);
			for (int i = data.size() - 1 ; i >= 0 ; i--) {
				HashMap<Comparable,DataElement> row = data.get(i);
				boolean removeRow = true;
				for (Comparable column : row.keySet()) {
					if (column.compareTo(lowColumn) >= 0) {
						removeRow = false;
						break;
					}
				}
				if (removeRow) {
					//LOGGER.info("Removing row " + data.indexOf(row));
					data.remove(i);
					rowKeys.remove(i);
				}
			}
		}
	}
	
	// Values2D interface method
	public int getRowCount() {
		return rowKeys.size();
	}
	// Values2D interface method
	public int getColumnCount() {
		int retVal = Math.min(columnKeys.size(),maxColumns);
		return retVal;
	}
	// Values2D interface method
	public Number getValue(int row, int column) {
		//LOGGER.info("("+row+","+column+")");
		if (data.get(row) == null) return null;
		// make column relative to maxColumns
		int newColumn = column;
		if (columnKeys.size() > maxColumns) {
			newColumn = columnKeys.size() - maxColumns + column;
		}
		Comparable columnKey = columnKeys.get(newColumn);
		DataElement element = data.get(row).get(columnKey);
		if (element == null) return null;
		return element.number;
	}

	// KeyedValues2D interface method
	public Comparable getRowKey(int row) {
		return rowKeys.get(row);
	}
	// KeyedValues2D interface method
	public int getRowIndex(Comparable key) {
		return rowKeys.indexOf(key);
	}
	// KeyedValues2D interface method
	public List getRowKeys() {
		return rowKeys;
	}
	// KeyedValues2D interface method
	public Comparable getColumnKey(int column) {
		// make column relative to maxColumns
		int newColumn = column;
		if (columnKeys.size() > maxColumns) {
			newColumn = columnKeys.size() - maxColumns + column;
		}
		return columnKeys.get(newColumn);
	}
	// KeyedValues2D interface method
	public int getColumnIndex(Comparable key) {
		return columnKeys.indexOf(key);
	}
	// KeyedValues2D interface method
	public List getColumnKeys() {
		int firstIndex = Math.max(0, columnKeys.size() - maxColumns);
		int lastIndex = Math.max(0, columnKeys.size() - 1);
		List retVal = columnKeys.subList(firstIndex, lastIndex);
		return retVal;
	}
	
	// KeyedValues2D interface method
	/**
	 * Gets the value with the given row and column keys.
	 * 
	 *  @param rowKey the row key
	 *  @param columnKey the column key
	 *  @return the value with the given row and column keys
	 */
	public Number getValue(Comparable rowKey, Comparable columnKey) {
		//LOGGER.info("("+rowKey+","+columnKey+")");
		int rowIndex = rowKeys.indexOf(rowKey);
		if (rowIndex == -1 || data.get(rowIndex) == null) return null;
		DataElement element = (DataElement) data.get(rowIndex).get(columnKey);
		if (element == null) return null;
		return element.number;
	}
	
	/**
	 * Returns the URL at the given row and column.
	 *
	 * @param row the row index
	 * @param column the column index
	 * @return the URL
	 */
	public String getUrl(int row, int column) {
		//LOGGER.info("("+row+","+column+")");
		if (data.get(row) == null) return null;
		// make column relative to maxColumns
		int newColumn = column;
		if (columnKeys.size() > maxColumns) {
			newColumn = columnKeys.size() - maxColumns + column;
		}
		Comparable columnKey = columnKeys.get(newColumn);
		DataElement element = data.get(row).get(columnKey);
		if (element == null) return null;
		return element.url;
	}

	/**
	 * Adds or updates a value.
	 *
	 * @param value the value to add
	 * @param url the URL to add and associate with the value
	 * @param rowKey the row key 
	 * @param columnKey the column key
	 */
	public void setValue(Number value, String url, Comparable rowKey, Comparable columnKey) {
		//LOGGER.info("Data point:"+value+","+url+","+rowKey+","+columnKey);
		int rowIndex = rowKeys.indexOf(rowKey);
		if (rowIndex == -1) {
			rowKeys.add(rowKey);
			rowIndex = rowKeys.size() - 1;
			data.add(new HashMap<Comparable,DataElement>());
		}
		if (! columnKeys.contains(columnKey)) {
			boolean added = false;
			for (int i = 0; i < columnKeys.size(); i++) {
				Comparable key = columnKeys.get(i);
				if (key.compareTo(columnKey) >= 0) {
					columnKeys.add(i,columnKey);
					added = true;
					break;
				}
			}
			if (!added) columnKeys.add(columnKey);
		}
		//LOGGER.info("columnKeys.size():"+columnKeys.size());
		DataElement element = new DataElement(value,url);
		data.get(rowIndex).put(columnKey, element);
	}
}
