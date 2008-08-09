drop table AUDIT_CLASS cascade constraints;
drop table AUDIT_CLASS_FIELD cascade constraints;
drop table AUDIT_EVENT cascade constraints;
drop table AUDIT_EVENT_PAIR cascade constraints;
drop table AUDIT_EVENT_PAIR_COLLECTION cascade constraints;
drop table AUDIT_TRANSACTION cascade constraints;
drop sequence AUDIT_CLASS_FIELD_ID_SEQUENCE;
drop sequence AUDIT_CLASS_ID_SEQUENCE;
drop sequence AUDIT_EVENT_ID_SEQUENCE;
drop sequence AUDIT_EVENT_PAIR_ID_SEQUENCE;
drop sequence AUDIT_TRANSACTION_ID_SEQUENCE;
create table AUDIT_CLASS (DTYPE char(1 char) not null, AUDIT_CLASS_ID NUMBER(30, 0) not null, CLASS_NAME varchar2(255 char), LABEL varchar2(255 char), ENTITY_ID_CLASS_NAME varchar2(255 char), COLLECTION_CLASS_NAME varchar2(255 char), primary key (AUDIT_CLASS_ID));
create table AUDIT_CLASS_FIELD (AUDIT_CLASS_FIELD_ID NUMBER(30, 0) not null, LABEL varchar2(255 char), FIELD_NAME varchar2(255 char) not null, AUDIT_CLASS_ID NUMBER(30, 0) not null, primary key (AUDIT_CLASS_FIELD_ID));
create table AUDIT_EVENT (AUDIT_EVENT_ID NUMBER(30, 0) not null, TARGET_ENTITY_ID NUMBER(30, 0), EVENT_TYPE varchar2(255 char) not null, AUDIT_TRANSACTION_ID NUMBER(30, 0) not null, AUDIT_CLASS_ID NUMBER(30, 0) not null, primary key (AUDIT_EVENT_ID));
create table AUDIT_EVENT_PAIR (IS_COLLECTION char(1 char) not null, AUDIT_EVENT_PAIR_ID NUMBER(30, 0) not null, STRING_VALUE varchar2(3000 char), AUDIT_CLASS_FIELD_ID NUMBER(30, 0) not null, AUDIT_EVENT_ID NUMBER(30, 0) not null, primary key (AUDIT_EVENT_PAIR_ID));
create table AUDIT_EVENT_PAIR_COLLECTION (AUDIT_EVENT_PAIR_ID NUMBER(30, 0) not null, COLLECTION_ENTITY_ID number(19,0) not null);
create table AUDIT_TRANSACTION (AUDIT_TRANSACTION_ID NUMBER(30, 0) not null, TRANSACTION_TMSTP timestamp, TRANSACTION_USER varchar2(255 char), primary key (AUDIT_TRANSACTION_ID));
alter table AUDIT_CLASS_FIELD add constraint FK62C99CAF63231DCC foreign key (AUDIT_CLASS_ID) references AUDIT_CLASS;
alter table AUDIT_EVENT add constraint FKED7388F663231DCC foreign key (AUDIT_CLASS_ID) references AUDIT_CLASS;
alter table AUDIT_EVENT add constraint FKED7388F625D28EF2 foreign key (AUDIT_TRANSACTION_ID) references AUDIT_TRANSACTION;
alter table AUDIT_EVENT_PAIR add constraint FK3EA9B483FC21623F foreign key (AUDIT_CLASS_FIELD_ID) references AUDIT_CLASS_FIELD;
alter table AUDIT_EVENT_PAIR add constraint FK3EA9B483B9EA7F72 foreign key (AUDIT_EVENT_ID) references AUDIT_EVENT;
alter table AUDIT_EVENT_PAIR_COLLECTION add constraint FKFBE35BDAA9E1677D foreign key (AUDIT_EVENT_PAIR_ID) references AUDIT_EVENT_PAIR;
create sequence AUDIT_CLASS_FIELD_ID_SEQUENCE;
create sequence AUDIT_CLASS_ID_SEQUENCE;
create sequence AUDIT_EVENT_ID_SEQUENCE;
create sequence AUDIT_EVENT_PAIR_ID_SEQUENCE;
create sequence AUDIT_TRANSACTION_ID_SEQUENCE;