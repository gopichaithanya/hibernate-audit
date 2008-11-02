package com.googlecode.hibernate.audit.test.logical_group_id;

import org.testng.annotations.Test;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.event.EventSource;
import org.hibernate.cfg.AnnotationConfiguration;
import com.googlecode.hibernate.audit.test.base.JTATransactionTest;
import com.googlecode.hibernate.audit.test.logical_group_id.data.A;
import com.googlecode.hibernate.audit.test.logical_group_id.data.B;
import com.googlecode.hibernate.audit.HibernateAudit;
import com.googlecode.hibernate.audit.HibernateAuditException;
import com.googlecode.hibernate.audit.LogicalGroupIdProvider;
import com.googlecode.hibernate.audit.RootIdProvider;
import com.googlecode.hibernate.audit.model.AuditTransaction;

import java.util.List;
import java.util.Random;
import java.io.Serializable;

/**
 * Tests the runtime API
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 *
 * Copyright 2008 Ovidiu Feodorov
 *
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
@Test(sequential = true)
public class LogicalGroupIdProviderTest extends JTATransactionTest
{
    // Constants -----------------------------------------------------------------------------------

    private static final Logger log = Logger.getLogger(LogicalGroupIdProviderTest.class);

    // Static --------------------------------------------------------------------------------------

    // Attributes ----------------------------------------------------------------------------------

    // Constructors --------------------------------------------------------------------------------

    // Public --------------------------------------------------------------------------------------

    @Test(enabled = true)
    public void testNullLogicalProviderId() throws Exception
    {
        AnnotationConfiguration config = new AnnotationConfiguration();
        config.configure(getHibernateConfigurationFileName());
        config.addAnnotatedClass(A.class);

        SessionFactoryImplementor sf = null;

        try
        {
            sf = (SessionFactoryImplementor)config.buildSessionFactory();

            HibernateAudit.startRuntime(sf.getSettings());
            HibernateAudit.register(sf);

            A a = new A();
            a.setName("alice");

            Session s = sf.openSession();
            s.beginTransaction();

            s.save(a);

            s.getTransaction().commit();

            List<AuditTransaction> atxs = HibernateAudit.getTransactions(a.getId());

            assert atxs.size() == 1;

            AuditTransaction at = atxs.get(0);

            log.debug(at);

            assert at.getLogicalGroupId() == null;
        }
        finally
        {
            HibernateAudit.stopRuntime();

            if (sf != null)
            {
                sf.close();
            }
        }
    }

    @Test(enabled = true)
    public void testConstantLogicalProviderId() throws Exception
    {
        AnnotationConfiguration config = new AnnotationConfiguration();
        config.configure(getHibernateConfigurationFileName());
        config.addAnnotatedClass(A.class);

        SessionFactoryImplementor sf = null;

        try
        {
            sf = (SessionFactoryImplementor)config.buildSessionFactory();

            final long random = new Random().nextLong();

            LogicalGroupIdProvider lgip = new LogicalGroupIdProvider()
            {
                public Serializable getLogicalGroupId(EventSource es,
                                                      Serializable id,
                                                      Object entity)
                {
                    return new Long(random);
                }
            };

            HibernateAudit.startRuntime(sf.getSettings());
            HibernateAudit.register(sf, lgip);

            A a = new A();
            a.setName("alice");

            Session s = sf.openSession();
            s.beginTransaction();

            s.save(a);

            s.getTransaction().commit();

            s.beginTransaction();

            a = new A();
            a.setName("anna");

            s.save(a);

            s.getTransaction().commit();

            List<AuditTransaction> atxs = HibernateAudit.getTransactions(null);

            assert atxs.size() == 2;

            for(AuditTransaction atx: atxs)
            {
                assert new Long(random).equals(atx.getLogicalGroupId());
            }
        }
        finally
        {
            HibernateAudit.stopRuntime();

            if (sf != null)
            {
                sf.close();
            }
        }
    }

    @Test(enabled = true)
    public void testInconsistentLogicalGroup() throws Exception
    {
        AnnotationConfiguration config = new AnnotationConfiguration();
        config.configure(getHibernateConfigurationFileName());
        config.addAnnotatedClass(A.class);
        config.addAnnotatedClass(B.class);

        SessionFactoryImplementor sf = null;

        try
        {
            sf = (SessionFactoryImplementor)config.buildSessionFactory();

            LogicalGroupIdProvider lgip = new LogicalGroupIdProvider()
            {
                public Serializable getLogicalGroupId(EventSource es,
                                                      Serializable id,
                                                      Object entity)
                {
                    if (entity instanceof A)
                    {
                        return new Long(10);
                    }
                    else if (entity instanceof B)
                    {
                        return new Long(20);
                    }

                    throw new IllegalStateException();
                }
            };

            HibernateAudit.startRuntime(sf.getSettings());
            HibernateAudit.register(sf, lgip);

            A a = new A();
            a.setName("alice");

            B b = new B();
            b.setName("bob");

            Session s = sf.openSession();
            s.beginTransaction();

            s.save(a);
            s.save(b);

            try
            {
                s.getTransaction().commit();
                throw new Error("should've failed");
            }
            catch(HibernateAuditException e)
            {
                Throwable cause = e.getCause();
                assert cause instanceof IllegalStateException;
                log.debug(">>> " + cause.getMessage());

                // transaction already rolled back
            }
        }
        finally
        {
            HibernateAudit.stopRuntime();

            if (sf != null)
            {
                sf.close();
            }
        }
    }

    @Test(enabled = true)
    public void testLatestTransactionByLogicalGroup_HBANotStarted() throws Exception
    {
        try
        {
            HibernateAudit.getLatestTransactionsByLogicalGroup("doesn't matter");
            throw new Error("should have failed");
        }
        catch(IllegalStateException e)
        {
            log.debug(">>> " + e.getMessage());
        }
    }

    @Test(enabled = true)
    public void testLatestTransactionByLogicalGroup_EmptyAuditTables() throws Exception
    {
        AnnotationConfiguration config = new AnnotationConfiguration();
        config.configure(getHibernateConfigurationFileName());
        SessionFactoryImplementor sf = null;

        try
        {
            sf = (SessionFactoryImplementor)config.buildSessionFactory();
            HibernateAudit.startRuntime(sf.getSettings());

            assert null == HibernateAudit.getLatestTransactionsByLogicalGroup("doesn't matter");
        }
        finally
        {
            HibernateAudit.stopRuntime();

            if (sf != null)
            {
                sf.close();
            }
        }
    }

    @Test(enabled = true)
    public void testLatestTransactionByLogicalGroup_NoSuchLogicalGroup() throws Exception
    {
        AnnotationConfiguration config = new AnnotationConfiguration();
        config.configure(getHibernateConfigurationFileName());
        config.addAnnotatedClass(A.class);
        SessionFactoryImplementor sf = null;

        try
        {
            sf = (SessionFactoryImplementor)config.buildSessionFactory();
            HibernateAudit.startRuntime(sf.getSettings());
            HibernateAudit.register(sf); // null logical group id

            Session s = sf.openSession();
            s.beginTransaction();

            A a = new A();
            s.save(a);

            s.getTransaction().commit();
            s.close();

            List<AuditTransaction> txs = HibernateAudit.getTransactions();
            assert txs.size() == 1;

            assert null == HibernateAudit.getLatestTransactionsByLogicalGroup("doesn't matter");
        }
        finally
        {
            HibernateAudit.stopRuntime();

            if (sf != null)
            {
                sf.close();
            }
        }
    }

    @Test(enabled = true)
    public void testLatestTransactionByLogicalGroup_OneRecord() throws Exception
    {
        AnnotationConfiguration config = new AnnotationConfiguration();
        config.configure(getHibernateConfigurationFileName());
        config.addAnnotatedClass(A.class);
        SessionFactoryImplementor sf = null;

        try
        {
            sf = (SessionFactoryImplementor)config.buildSessionFactory();
            HibernateAudit.startRuntime(sf.getSettings());
            RootIdProvider rip = new RootIdProvider(A.class);
            HibernateAudit.register(sf, rip); // null logical group id

            Session s = sf.openSession();
            s.beginTransaction();

            A a = new A();
            rip.setRoot(a);
            s.save(a);

            s.getTransaction().commit();
            s.close();

            List<AuditTransaction> txs = HibernateAudit.getTransactions();
            assert txs.size() == 1;

            AuditTransaction tx = HibernateAudit.getLatestTransactionsByLogicalGroup(a.getId());

            assert a.getId().equals(tx.getLogicalGroupId());
        }
        finally
        {
            HibernateAudit.stopRuntime();

            if (sf != null)
            {
                sf.close();
            }
        }
    }

    @Test(enabled = true)
    public void testLatestTransactionByLogicalGroup_TwoRecords() throws Exception
    {
        AnnotationConfiguration config = new AnnotationConfiguration();
        config.configure(getHibernateConfigurationFileName());
        config.addAnnotatedClass(A.class);
        SessionFactoryImplementor sf = null;

        try
        {
            sf = (SessionFactoryImplementor)config.buildSessionFactory();
            HibernateAudit.startRuntime(sf.getSettings());
            RootIdProvider rip = new RootIdProvider(A.class);
            HibernateAudit.register(sf, rip); // null logical group id

            Session s = sf.openSession();
            s.beginTransaction();

            A a = new A();
            rip.setRoot(a);
            s.save(a);

            s.getTransaction().commit();
            s.close();

            List<AuditTransaction> txs = HibernateAudit.getTransactions();
            assert txs.size() == 1;
            AuditTransaction tx1 = txs.get(0);

            AuditTransaction tx = HibernateAudit.getLatestTransactionsByLogicalGroup(a.getId());
            assert a.getId().equals(tx.getLogicalGroupId());


            s = sf.openSession();
            s.beginTransaction();

            a = (A)s.get(A.class, a.getId());
            rip.setRoot(a);

            a.setName("blah");
            s.update(a);

            s.getTransaction().commit();
            s.close();

            txs = HibernateAudit.getTransactions();
            assert txs.size() == 2;
            assert tx1.getId().equals(txs.get(0).getId());
            AuditTransaction tx2 = txs.get(1);

            tx = HibernateAudit.getLatestTransactionsByLogicalGroup(a.getId());
            assert tx2.getId().equals(tx.getId());
            assert a.getId().equals(tx2.getLogicalGroupId());
        }
        finally
        {
            HibernateAudit.stopRuntime();
            
            if (sf != null)
            {
                sf.close();
            }
        }
    }

    // Package protected ---------------------------------------------------------------------------

    // Protected -----------------------------------------------------------------------------------

    // Private -------------------------------------------------------------------------------------

    // Inner classes -------------------------------------------------------------------------------
}
