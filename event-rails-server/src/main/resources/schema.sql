CREATE TABLE INT_LOCK  (
						   LOCK_KEY CHAR(36) NOT NULL,
						   REGION VARCHAR(100) NOT NULL,
						   CLIENT_ID CHAR(36),
						   CREATED_DATE DATETIME(6) NOT NULL,
						   constraint INT_LOCK_PK primary key (LOCK_KEY, REGION)
) ENGINE=InnoDB;


CREATE PROCEDURE PUBLISH_EVENT(
	IN in__event_id VARCHAR(255),
	IN in__aggregate_id VARCHAR(255),
	IN in__event_message JSON,
	IN in__event_name VARCHAR(255)
)
BEGIN
	LOCK TABLES es__events WRITE;
	INSERT INTO es__events
	(event_id, aggregate_id, aggregate_sequence_number, created_at, event_message, event_name, event_sequence_number)
	select
		in__event_id,
		in__aggregate_id,
		(select ifnull(max(aggregate_sequence_number) + 1,1) from es__events where aggregate_id = in__aggregate_id),
		CURRENT_TIMESTAMP(),
		in__event_message,
		in__event_name,
		ifnull(max(event_sequence_number) + 1,1) from es__events;
	UNLOCK TABLES;
END