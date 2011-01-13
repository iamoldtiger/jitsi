/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.addrbook;

import java.util.*;
import java.util.regex.*;

import net.java.sip.communicator.service.contactsource.*;
import net.java.sip.communicator.service.protocol.*;

/**
 * Provides an abstract implementation of a <tt>ContactQuery</tt> which runs in
 * a separate <tt>Thread</tt>.
 *
 * @author Lyubomir Marinov
 * @param <T> the very type of <tt>ContactSourceService</tt> which performs the
 * <tt>ContactQuery</tt>
 */
public abstract class AsyncContactQuery<T extends ContactSourceService>
    extends AbstractContactQuery<T>
{

    /**
     * The <tt>Pattern</tt> for which the associated
     * <tt>ContactSourceService</tt> is being queried.
     */
    protected final Pattern query;

    /**
     * The <tt>SourceContact</tt>s which match {@link #query}.
     */
    private final List<SourceContact> queryResults
        = new LinkedList<SourceContact>();

    /**
     * The <tt>Thread</tt> in which this <tt>AsyncContactQuery</tt> is
     * performing {@link #query}.
     */
    private Thread thread;

    /**
     * Initializes a new <tt>AsyncContactQuery</tt> instance which is to perform
     * a specific <tt>query</tt> on behalf of a specific <tt>contactSource</tt>.
     *
     * @param contactSource the <tt>ContactSourceService</tt> which is to
     * perform the new <tt>ContactQuery</tt> instance
     * @param query the <tt>Pattern</tt> for which <tt>contactSource</tt> is
     * being queried
     */
    protected AsyncContactQuery(T contactSource, Pattern query)
    {
        super(contactSource);

        this.query = query;
    }

    /**
     * Adds a specific <tt>SourceContact</tt> to the list of
     * <tt>SourceContact</tt>s to be returned by this <tt>ContactQuery</tt> in
     * response to {@link #getQueryResults()}.
     *
     * @param sourceContact the <tt>SourceContact</tt> to be added to the
     * <tt>queryResults</tt> of this <tt>ContactQuery</tt>
     * @return <tt>true</tt> if the <tt>queryResults</tt> of this
     * <tt>ContactQuery</tt> has changed in response to the call
     */
    protected boolean addQueryResult(SourceContact sourceContact)
    {
        boolean changed;

        synchronized (queryResults)
        {
            changed = queryResults.add(sourceContact);
        }
        if (changed)
            fireContactReceived(sourceContact);
        return changed;
    }

    /**
     * Gets the number of <tt>SourceContact</tt>s which match this
     * <tt>ContactQuery</tt>.
     *
     * @return the number of <tt>SourceContact</tt> which match this
     * <tt>ContactQuery</tt>
     */
    public int getQueryResultCount()
    {
        synchronized (queryResults)
        {
            return queryResults.size();
        }
    }

    /**
     * Gets the <tt>List</tt> of <tt>SourceContact</tt>s which match this
     * <tt>ContactQuery</tt>.
     *
     * @return the <tt>List</tt> of <tt>SourceContact</tt>s which match this
     * <tt>ContactQuery</tt>
     * @see ContactQuery#getQueryResults()
     */
    public List<SourceContact> getQueryResults()
    {
        List<SourceContact> qr;

        synchronized (queryResults)
        {
            qr = new ArrayList<SourceContact>(queryResults.size());
            qr.addAll(queryResults);
        }
        return qr;
    }

    /**
     * Normalizes a <tt>String</tt> phone number by converting alpha characters
     * to their respective digits on a keypad and then stripping non-digit
     * characters.
     * 
     * @param phoneNumber a <tt>String</tt> which represents a phone number to
     * normalize
     * @return a <tt>String</tt> which is a normalized form of the specified
     * <tt>phoneNumber</tt>
     */
    protected String normalizePhoneNumber(String phoneNumber)
    {
        PhoneNumberI18nService phoneNumberI18nService
            = AddrBookActivator.getPhoneNumberI18nService();

        return
            (phoneNumberI18nService == null)
                ? phoneNumber
                : phoneNumberI18nService.normalize(phoneNumber);
    }

    /**
     * Performs this <tt>ContactQuery</tt> in a background <tt>Thread</tt>.
     */
    protected abstract void run();

    /**
     * Starts this <tt>AsyncContactQuery</tt>.
     */
    public synchronized void start()
    {
        if (thread == null)
        {
            thread
                = new Thread()
                {
                    @Override
                    public void run()
                    {
                        boolean completed = false;

                        try
                        {
                            AsyncContactQuery.this.run();
                            completed = true;
                        }
                        finally
                        {
                            synchronized (AsyncContactQuery.this)
                            {
                                if (thread == Thread.currentThread())
                                    stopped(completed);
                            }
                        }
                    }
                };
            thread.setDaemon(true);
            thread.start();
        }
        else
            throw new IllegalStateException("thread");
    }

    /**
     * Notifies this <tt>AsyncContactQuery</tt> that it has stopped performing
     * in the associated background <tt>Thread</tt>.
     *
     * @param completed <tt>true</tt> if this <tt>ContactQuery</tt> has
     * successfully completed, <tt>false</tt> if an error has been encountered
     * during its execution
     */
    protected void stopped(boolean completed)
    {
        if (getStatus() == QUERY_IN_PROGRESS)
            setStatus(completed ? QUERY_COMPLETED : QUERY_ERROR);
    }
}