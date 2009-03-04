/**
 * 
 */
package com.googlecode.hibernate.audit.listener;

import java.util.Collection;
import java.util.Iterator;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.SessionFactoryObserver;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.stat.Statistics;
import org.hibernate.type.AbstractComponentType;
import org.hibernate.type.CollectionType;
import org.hibernate.type.ComponentType;
import org.hibernate.type.EntityType;
import org.hibernate.type.Type;

import com.googlecode.hibernate.audit.HibernateAudit;
import com.googlecode.hibernate.audit.configuration.AuditConfiguration;
import com.googlecode.hibernate.audit.model.clazz.AuditType;
import com.googlecode.hibernate.audit.model.clazz.AuditTypeField;

public class AuditSessionFactoryObserver implements SessionFactoryObserver {
	private static final Logger log = Logger
			.getLogger(AuditSessionFactoryObserver.class);

	private SessionFactoryObserver observer;
	private AuditConfiguration auditConfiguration;
	private Configuration configuration;

	public AuditSessionFactoryObserver(SessionFactoryObserver observer,
			AuditConfiguration auditConfiguration, Configuration configuration) {
		this.observer = observer;
		this.auditConfiguration = auditConfiguration;
		this.configuration = configuration;
	}

	public void sessionFactoryCreated(SessionFactory sessionfactory) {
		initializeAuditMetatdata(sessionfactory);

		Statistics statistics = sessionfactory.getStatistics();

		if (observer != null) {
			observer.sessionFactoryCreated(sessionfactory);
		}
	}

	public void sessionFactoryClosed(SessionFactory sessionfactory) {
		if (observer != null) {
			observer.sessionFactoryClosed(sessionfactory);
		}
	}

	private void initializeAuditMetatdata(SessionFactory sessionFactory) {
		Collection<ClassMetadata> allClassMetadata = sessionFactory
				.getAllClassMetadata().values();

		if (log.isEnabledFor(Level.INFO)) {
			log.info("Start building audit log metadata.");
		}
		Session session = null;

		try {
			try {
				session = sessionFactory.openSession();
				Transaction tx = session.beginTransaction();

				for (ClassMetadata classMetadata : allClassMetadata) {
					String entityName = classMetadata.getEntityName();

					if (auditConfiguration.getExtensionManager()
							.getAuditableInformationProvider().isAuditable(
									entityName)) {

						initializeEntityAuditType(session, entityName, true);
						session.flush();
					}
				}

				tx.commit();
			} finally {
				if (session != null) {
					session.close();
				}
			}
		} finally {
			if (log.isEnabledFor(Level.INFO)) {
				log.info("End building audit log metadata.");
			}
		}
	}

	private AuditType initializeEntityAuditType(Session session,
			String entityName, boolean initializeProperties) {
		PersistentClass classMapping = configuration
				.getClassMapping(entityName);
		Class mappedClass = classMapping.getMappedClass();

		AuditType auditType = HibernateAudit.getAuditType(session, mappedClass
				.getName());
		if (auditType == null) {
			auditType = new AuditType();
			auditType.setClassName(mappedClass.getName());
			auditType.setLabel(entityName);
			auditType.setType(AuditType.ENTITY_TYPE);

			session.save(auditType);
			updateMetaModel(session);
		}

		if (initializeProperties) {
			Property identifierProperty = classMapping.getIdentifierProperty();
			if (identifierProperty != null) {
				initializeAuditField(session, mappedClass, auditType,
						identifierProperty.getName(), identifierProperty
								.getType());
			}

			for (Iterator propertyIterator = classMapping
					.getPropertyClosureIterator(); propertyIterator.hasNext();) {
				Property property = (Property) propertyIterator.next();
				initializeAuditField(session, mappedClass, auditType, property
						.getName(), property.getType());
			}
		}
		return auditType;
	}

	private AuditType initializeComponentAuditType(Session session,
			AbstractComponentType type) {
		Class returnedClass = type.getReturnedClass();

		AuditType componentAuditType = HibernateAudit.getAuditType(session,
				returnedClass.getName());
		if (componentAuditType == null) {
			componentAuditType = new AuditType();
			componentAuditType.setClassName(returnedClass.getName());
			componentAuditType.setType(AuditType.COMPONENT_TYPE);
			session.save(componentAuditType);
			updateMetaModel(session);
		}

		String[] componentPropertyNames = type.getPropertyNames();
		if (componentPropertyNames != null) {
			for (int i = 0; i < componentPropertyNames.length; i++) {
				AuditTypeField componentAuditField = initializeAuditField(
						session, returnedClass, componentAuditType,
						componentPropertyNames[i], type.getSubtypes()[i]);

			}
		}
		return componentAuditType;
	}

	private AuditType initializePrimitiveAuditType(Session session, Type type) {
		AuditType auditType = HibernateAudit.getAuditType(session, type
				.getReturnedClass().getName());

		if (auditType == null) {
			auditType = new AuditType();
			auditType.setClassName(type.getReturnedClass().getName());
			if (type.isCollectionType()) {
				auditType.setType(AuditType.COLLECTION_TYPE);
			} else {
				auditType.setType(AuditType.PRIMITIVE_TYPE);
			}
			session.save(auditType);
			updateMetaModel(session);
		}

		return auditType;
	}

	private AuditTypeField initializeAuditField(Session session,
			Class ownerClass, AuditType auditType, String propertyName,
			Type type) {

		AuditTypeField auditField = HibernateAudit.getAuditField(session,
				ownerClass.getName(), propertyName);

		if (auditField == null) {
			auditField = new AuditTypeField();
			auditField.setName(propertyName);
			auditField.setOwnerType(auditType);
			auditType.getAuditFields().add(auditField);

			AuditType auditFieldType = null;

			if (type.isCollectionType()) {
				auditFieldType = initializePrimitiveAuditType(session, type);

				Type elementType = ((CollectionType) type)
						.getElementType((SessionFactoryImplementor) session
								.getSessionFactory());

				if (elementType.isCollectionType()) {
					// do nothing..
				} else if (elementType.isComponentType()) {
					initializeComponentAuditType(session,
							(ComponentType) elementType);
				} else if (elementType.isEntityType()) {
					// do nothing .. it will be handled during the entity model
					// traverse
				} else {
					initializePrimitiveAuditType(session, elementType);
				}
			} else if (type.isComponentType()) {
				auditFieldType = initializeComponentAuditType(session,
						(AbstractComponentType) type);
			} else if (type.isEntityType()) {
				auditFieldType = initializeEntityAuditType(session,
						((EntityType) type).getName(), false);
			} else {
				auditFieldType = initializePrimitiveAuditType(session, type);
			}

			auditField.setFieldType(auditFieldType);
			session.save(auditField);

			updateMetaModel(session);
		}

		return auditField;
	}

	private void updateMetaModel(Session session) {
		session.flush();
		session.getSessionFactory().evictQueries(
				HibernateAudit.AUDIT_META_DATA_QUERY_CACHE_REGION);
		session.getSessionFactory().evictEntity(AuditType.class.getName());
		session.getSessionFactory().evictEntity(AuditTypeField.class.getName());
	}
}