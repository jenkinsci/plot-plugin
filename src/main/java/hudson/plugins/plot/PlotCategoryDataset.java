/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.AbstractDataset;

/**
 * A {@link CategoryDataset} implementation that stores numeric data points and
 * corresponding URLs. This data structure is basically a table with row and
 * column names (keys).
 *
 * @author Nigel Daley
 */
public class PlotCategoryDataset extends AbstractDataset implements CategoryDataset {
    @Serial
    private static final long serialVersionUID = 9215482265757674967L;

    private static class DataElement {
        private final Number number;
        private final String url;

        DataElement(Number n, String u) {
            this.number = n;
            this.url = u;
        }
    }

    /**
     * The row keys
     */
    private transient List<Comparable> rowKeys;

    /**
     * The column keys
     */
    private transient List<Comparable> columnKeys;

    /**
     * The row data
     */
    private transient List<Map<Comparable, DataElement>> data;

    /**
     * The max number of builds to plot
     */
    private transient int maxColumns;

    /**
     * Creates a new empty instance.
     */
    public PlotCategoryDataset() {
        this.rowKeys = new ArrayList<>();
        this.columnKeys = new ArrayList<>();
        this.data = new ArrayList<>();
    }

    /**
     * Truncates the dataset to the <i>last</i> <code>maxColumns</code> columns.
     *
     * @param maxColumns the maximum number columns that will appear to be in the dataset.
     */
    public void clipDataset(int maxColumns) {
        this.maxColumns = maxColumns;
        // Columns are lazily truncated when the data is queried.
        // Rows that contain no data when the columns are truncated are
        // removed here so that they don't show up in plot legends.
        if (getColumnCount() > 0) {
            Comparable lowColumn = getColumnKey(0);
            for (int i = data.size() - 1; i >= 0; i--) {
                Map<Comparable, DataElement> row = data.get(i);
                boolean removeRow = true;
                for (Comparable column : row.keySet()) {
                    if (column.compareTo(lowColumn) >= 0) {
                        removeRow = false;
                        break;
                    }
                }
                if (removeRow) {
                    // LOGGER.info("Removing row " + data.indexOf(row));
                    data.remove(i);
                    rowKeys.remove(i);
                }
            }
        }
    }

    @Override
    public int getRowCount() {
        return rowKeys.size();
    }

    @Override
    public int getColumnCount() {
        return Math.min(columnKeys.size(), maxColumns);
    }

    @Override
    public Number getValue(int row, int column) {
        // LOGGER.info("("+row+","+column+")");
        if (data.get(row) == null) {
            return null;
        }
        // make column relative to maxColumns
        int newColumn = column;
        if (columnKeys.size() > maxColumns) {
            newColumn = columnKeys.size() - maxColumns + column;
        }
        Comparable columnKey = columnKeys.get(newColumn);
        DataElement element = data.get(row).get(columnKey);
        if (element == null) {
            return null;
        }
        return element.number;
    }

    @Override
    public Comparable getRowKey(int row) {
        return rowKeys.get(row);
    }

    @Override
    public int getRowIndex(Comparable key) {
        return rowKeys.indexOf(key);
    }

    @Override
    public List getRowKeys() {
        return rowKeys;
    }

    @Override
    public Comparable getColumnKey(int column) {
        // make column relative to maxColumns
        int newColumn = column;
        if (columnKeys.size() > maxColumns) {
            newColumn = columnKeys.size() - maxColumns + column;
        }
        return columnKeys.get(newColumn);
    }

    @Override
    public int getColumnIndex(Comparable key) {
        return columnKeys.indexOf(key);
    }

    @Override
    public List getColumnKeys() {
        int firstIndex = Math.max(0, columnKeys.size() - maxColumns);
        int lastIndex = Math.max(0, columnKeys.size());
        return columnKeys.subList(firstIndex, lastIndex);
    }

    /**
     * Gets the value with the given row and column keys.
     *
     * @param rowKey    the row key
     * @param columnKey the column key
     * @return the value with the given row and column keys
     */
    @Override
    public Number getValue(Comparable rowKey, Comparable columnKey) {
        // LOGGER.info("("+rowKey+","+columnKey+")");
        int rowIndex = rowKeys.indexOf(rowKey);
        if (rowIndex == -1 || data.get(rowIndex) == null) {
            return null;
        }
        DataElement element = data.get(rowIndex).get(columnKey);
        if (element == null) {
            return null;
        }
        return element.number;
    }

    /**
     * Returns the URL at the given row and column.
     *
     * @param row    the row index
     * @param column the column index
     * @return the URL
     */
    public String getUrl(int row, int column) {
        // LOGGER.info("("+row+","+column+")");
        if (data.get(row) == null) {
            return null;
        }
        // make column relative to maxColumns
        int newColumn = column;
        if (columnKeys.size() > maxColumns) {
            newColumn = columnKeys.size() - maxColumns + column;
        }
        Comparable columnKey = columnKeys.get(newColumn);
        DataElement element = data.get(row).get(columnKey);
        if (element == null) {
            return null;
        }
        return element.url;
    }

    /**
     * Adds or updates a value.
     *
     * @param value     the value to add
     * @param url       the URL to add and associate with the value
     * @param rowKey    the row key
     * @param columnKey the column key
     */
    public void setValue(Number value, String url, Comparable rowKey, Comparable columnKey) {
        // LOGGER.info("Data point:"+value+","+url+","+rowKey+","+columnKey);
        int rowIndex = rowKeys.indexOf(rowKey);
        if (rowIndex == -1) {
            rowKeys.add(rowKey);
            rowIndex = rowKeys.size() - 1;
            data.add(new HashMap<>());
        }
        if (!columnKeys.contains(columnKey)) {
            boolean added = false;
            for (int i = 0; i < columnKeys.size(); i++) {
                Comparable key = columnKeys.get(i);
                if (key.compareTo(columnKey) >= 0) {
                    columnKeys.add(i, columnKey);
                    added = true;
                    break;
                }
            }
            if (!added) {
                columnKeys.add(columnKey);
            }
        }
        // LOGGER.info("columnKeys.size():"+columnKeys.size());
        DataElement element = new DataElement(value, url);
        data.get(rowIndex).put(columnKey, element);
    }
}
