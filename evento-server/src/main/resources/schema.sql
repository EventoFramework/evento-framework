CREATE TABLE IF NOT EXISTS `int_lock` (
							`lock_key` char(36) NOT NULL,
							`region` varchar(100) NOT NULL,
							`client_id` char(36) DEFAULT NULL,
							`created_date` datetime(6) NOT NULL,
							PRIMARY KEY (`lock_key`,`region`)
) ENGINE=InnoDB;



CREATE TABLE IF NOT EXISTS `es__events` (
							  `event_sequence_number` bigint NOT NULL,
							  `aggregate_id` varchar(255) DEFAULT NULL,
							  `created_at` bigint NOT NULL,
							  `event_message` blob NOT NULL,
							  `event_name` varchar(255) NOT NULL,
							  KEY `es__events_aggregate_id_event_sequence_number_index` (`aggregate_id`,`event_sequence_number`),
							  KEY `es__events_event_name_event_sequence_number_index` (`event_name`,`event_sequence_number`),
							  KEY `es__events_event_sequence_number_index` (`event_sequence_number`)
) ENGINE=InnoDB;




