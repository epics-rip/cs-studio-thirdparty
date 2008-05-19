package org.csstudio.logbook.sns;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import oracle.sql.BLOB;

import org.csstudio.logbook.ILogbook;

/**
 * SNS logbook
 * 
 * @author nypaver
 * @author Kay Kasemir
 */
public class SNSLogbook implements ILogbook
{
    private static final String DEFAULT_BADGE_NUMBER = "999992"; //$NON-NLS-1$
    final private Connection connection;
    final private String logbook;
    final String badge_number;

    /** Constructor
     *  @param connection RDB connection
     *  @param user User (for which we'll try to get the badge number)
     *  @param logbook SNS logbook to use
     *  @throws Exception on error
     */
    public SNSLogbook(final Connection connection, final String user,
            final String logbook) throws Exception
    {
        this.connection = connection;
        this.logbook = logbook;
        badge_number = getBadgeNumber(user);
    }

    @SuppressWarnings("nls")
    public void createEntry(String title, String text, String imageName)
            throws Exception
    {
        if (imageName == null)
        {
            final String mysql = "call logbook.logbook_pkg.insert_logbook_entry"
                    + "(?, ?, ?, ?, ?)";
            final String category = "";

            // create an sql call for the elog
            final CallableStatement statement = connection.prepareCall(mysql);
            try
            {
                statement.setString(1, badge_number);
                statement.setString(2, logbook);
                statement.setString(3, title);
                statement.setString(4, category);
                statement.setString(5, text);
                statement.executeQuery();
            }
            finally
            {
                statement.close();
            }
        }
        else
        {
            final File imgFile = new File(imageName);
            // Read the file into the byte array
            final byte byteValue[] = getBytesFromFile(imgFile);
            // Add the byte to the entity bean field
            final Blob blob = BLOB.createTemporary(connection, true,
                        BLOB.DURATION_SESSION);
            blob.setBytes(1L, byteValue);

            final String mysql = "call logbook.logbook_pkg.insert_logbook_entry"
                        + "(?, ?, ?, ?, ?, ?, ?, ?)";
            final CallableStatement statement = connection.prepareCall(mysql);
            try
            {
                statement.setString(1, badge_number);
                statement.setString(2, logbook);
                statement.setString(3, title);
                statement.setString(4, text);
                statement.setString(5, "I");
                statement.setString(6, imageName);
                statement.setLong(7, 1);
                statement.setBlob(8, blob);
                statement.executeQuery();
            }
            finally
            {
                statement.close();
            }
        }
    }
    
    /** Get the badge number for the user in the connection dictionary
     *  @return the badge number for the specified user or a default
     */
    @SuppressWarnings("nls")
    private String getBadgeNumber(final String user) throws Exception
    {
        final PreparedStatement statement = connection
        .prepareStatement("select bn from OPER.EMPLOYEE_V where user_id=?");
        try
        {
            // OPER.EMPLOYEE_V seems to only keep uppercase user_id entries
            statement.setString(1, user.trim().toUpperCase());
            statement.execute();
            final ResultSet result = statement.getResultSet();
            if (result.next())
            {
                final String badge = result.getString("bn");
                if (badge.length() > 1)
                    return badge;
            }
            // No error, but also not found: fall through
        }
        finally
        {
            statement.close();
        }
        return DEFAULT_BADGE_NUMBER;
    }

    public void close()
    {
        try
        {
            connection.close();
        }
        catch (Exception ex)
        {
            // TODO Replace with call to CentralLogger.getLogger().error(ex);
            ex.printStackTrace();
        }
    }

    /**
     * returns the byte array to the corresponding file
     * 
     * @param file
     * @return byte[]
     * @throws IOException on error
     */
    @SuppressWarnings("nls")
    public static byte[] getBytesFromFile(final File file) throws IOException
    {
        final InputStream is = new FileInputStream(file);

        // Get the size of the file
        final long length = file.length();

        // You cannot create an array using a long type.
        // It needs to be an int type.
        // Before converting to an int type, check
        // to ensure that file is not larger than Integer.MAX_VALUE.
        if (length > Integer.MAX_VALUE)
            throw new IOException("Image file " + file.getName() + " is too large.");
      
        // Create the byte array to hold the data
        final byte[] bytes = new byte[(int) length];

        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
                && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0)
        {
            offset += numRead;
        }

        //System.out.println(offset + " " + bytes.length);

        // Ensure all the bytes have been read in
        if (offset < bytes.length)
        {
            throw new IOException("Could not completely read file "
                    + file.getName());
        }
        // Close the input stream and return bytes
        is.close();

        return bytes;
    }
}
