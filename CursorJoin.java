import android.database.Cursor;

import java.util.Iterator;

/**
 * Created by Shahar Barsheshet on 2/23/14.
 * <p/>
 * This class was created to handle Cursor joining for Long values.
 * as @{CursorJoiner} handle only String values.
 */
public class CursorJoin implements Iterator<CursorJoin.Result>, Iterable<CursorJoin.Result> {
    private Cursor mCursorLeft;
    private Cursor mCursorRight;
    private boolean mCompareResultIsValid;
    private Result mCompareResult;
    private int[] mColumnsLeft;
    private int[] mColumnsRight;
    private Long[] mValues;

    /**
     * Initializes the CursorJoiner and resets the cursors to the first row. The left and right
     * column name arrays must have the same number of columns.
     *
     * @param cursorLeft       The left cursor to compare
     * @param columnNamesLeft  The column names to compare from the left cursor
     * @param cursorRight      The right cursor to compare
     * @param columnNamesRight The column names to compare from the right cursor
     */
    public CursorJoin(
            Cursor cursorLeft, String[] columnNamesLeft,
            Cursor cursorRight, String[] columnNamesRight) {
        if (columnNamesLeft.length != columnNamesRight.length) {
            throw new IllegalArgumentException(
                    "you must have the same number of columns on the left and right, "
                            + columnNamesLeft.length + " != " + columnNamesRight.length);
        }

        mCursorLeft = cursorLeft;
        mCursorRight = cursorRight;

        mCursorLeft.moveToFirst();
        mCursorRight.moveToFirst();

        mCompareResultIsValid = false;

        mColumnsLeft = buildColumnIndiciesArray(cursorLeft, columnNamesLeft);
        mColumnsRight = buildColumnIndiciesArray(cursorRight, columnNamesRight);

        mValues = new Long[mColumnsLeft.length * 2];
    }

    /**
     * Reads the strings from the cursor that are specifed in the columnIndicies
     * array and saves them in values beginning at startingIndex, skipping a slot
     * for each value. If columnIndicies has length 3 and startingIndex is 1, the
     * values will be stored in slots 1, 3, and 5.
     *
     * @param values         the String[] to populate
     * @param cursor         the cursor from which to read
     * @param columnIndicies the indicies of the values to read from the cursor
     * @param startingIndex  the slot in which to start storing values, and must be either 0 or 1.
     */
    private static void populateValues(Long[] values, Cursor cursor, int[] columnIndicies,
                                       int startingIndex) {
        assert startingIndex == 0 || startingIndex == 1;
        for (int i = 0; i < columnIndicies.length; i++) {
            values[startingIndex + i * 2] = cursor.getLong(columnIndicies[i]);
        }
    }

    /**
     * Compare the values. Values contains n pairs of long. If all the pairs of longs match
     * then returns 0. Otherwise returns the comparison result of the first non-matching pair
     * of values, -1 if the first of the pair is less than the second of the pair or 1 if it
     * is greater.
     *
     * @param values the n pairs of values to compare
     * @return -1, 0, or 1 as described above.
     */
    private static int compareValues(Long... values) {
        if ((values.length % 2) != 0) {
            throw new IllegalArgumentException("you must specify an even number of values");
        }

        for (int index = 0; index < values.length; index += 2) {
            if (values[index] == null) {
                if (values[index + 1] == null) continue;
                return -1;
            }

            if (values[index + 1] == null) {
                return 1;
            }

            int comp = values[index].compareTo(values[index + 1]);
            if (comp != 0) {
                return comp < 0 ? -1 : 1;
            }
        }
        return 0;
    }

    public Iterator<Result> iterator() {
        return this;
    }

    /**
     * Lookup the indicies of the each column name and return them in an array.
     *
     * @param cursor      the cursor that contains the columns
     * @param columnNames the array of names to lookup
     * @return an array of column indices
     */
    private int[] buildColumnIndiciesArray(Cursor cursor, String[] columnNames) {
        int[] columns = new int[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            columns[i] = cursor.getColumnIndexOrThrow(columnNames[i]);
        }
        return columns;
    }

    /**
     * Returns whether or not there are more rows to compare using next().
     *
     * @return true if there are more rows to compare
     */
    public boolean hasNext() {
        if (mCompareResultIsValid) {
            switch (mCompareResult) {
                case BOTH:
                    return !mCursorLeft.isLast() || !mCursorRight.isLast();

                case LEFT:
                    return !mCursorLeft.isLast() || !mCursorRight.isAfterLast();

                case RIGHT:
                    return !mCursorLeft.isAfterLast() || !mCursorRight.isLast();

                default:
                    throw new IllegalStateException("bad value for mCompareResult, "
                            + mCompareResult);
            }
        } else {
            return !mCursorLeft.isAfterLast() || !mCursorRight.isAfterLast();
        }
    }

    /**
     * Returns the comparison result of the next row from each cursor. If one cursor
     * has no more rows but the other does then subsequent calls to this will indicate that
     * the remaining rows are unique.
     * <p/>
     * The caller must check that hasNext() returns true before calling this.
     * <p/>
     * Once next() has been called the cursors specified in the result of the call to
     * next() are guaranteed to point to the row that was indicated. Reading values
     * from the cursor that was not indicated in the call to next() will result in
     * undefined behavior.
     *
     * @return LEFT, if the row pointed to by the left cursor is unique, RIGHT
     * if the row pointed to by the right cursor is unique, BOTH if the rows in both
     * cursors are the same.
     */
    public Result next() {
        if (!hasNext()) {
            throw new IllegalStateException("you must only call next() when hasNext() is true");
        }
        incrementCursors();
        assert hasNext();

        boolean hasLeft = !mCursorLeft.isAfterLast();
        boolean hasRight = !mCursorRight.isAfterLast();

        if (hasLeft && hasRight) {
            populateValues(mValues, mCursorLeft, mColumnsLeft, 0 /* start filling at index 0 */);
            populateValues(mValues, mCursorRight, mColumnsRight, 1 /* start filling at index 1 */);
            switch (compareValues(mValues)) {
                case -1:
                    mCompareResult = Result.LEFT;
                    break;
                case 0:
                    mCompareResult = Result.BOTH;
                    break;
                case 1:
                    mCompareResult = Result.RIGHT;
                    break;
            }
        } else if (hasLeft) {
            mCompareResult = Result.LEFT;
        } else {
            assert hasRight;
            mCompareResult = Result.RIGHT;
        }
        mCompareResultIsValid = true;
        return mCompareResult;
    }

    public void remove() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Increment the cursors past the rows indicated in the most recent call to next().
     * This will only have an affect once per call to next().
     */
    private void incrementCursors() {
        if (mCompareResultIsValid) {
            switch (mCompareResult) {
                case LEFT:
                    mCursorLeft.moveToNext();
                    break;
                case RIGHT:
                    mCursorRight.moveToNext();
                    break;
                case BOTH:
                    mCursorLeft.moveToNext();
                    mCursorRight.moveToNext();
                    break;
            }
            mCompareResultIsValid = false;
        }
    }

    /**
     * The result of a call to next().
     */
    public enum Result {
        /**
         * The row currently pointed to by the left cursor is unique
         */
        RIGHT,
        /**
         * The row currently pointed to by the right cursor is unique
         */
        LEFT,
        /**
         * The rows pointed to by both cursors are the same
         */
        BOTH
    }
}
