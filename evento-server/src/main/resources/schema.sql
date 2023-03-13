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
							  KEY `es__events_aggregate_id_event_sequence_number_index` (`aggregate_id`,`event_sequence_number`) using BTREE ,
							  KEY `es__events_event_name_event_sequence_number_index` (`event_name`,`event_sequence_number`) using BTREE ,
							  KEY `es__events_event_sequence_number_index` (`event_sequence_number`) using BTREE
) ENGINE=InnoDB ;

CREATE TABLE IF NOT EXISTS `es__snapshot` (
								`aggregate_id` varchar(255) NOT NULL,
								`aggregate_state` blob,
								`event_sequence_number` bigint DEFAULT NULL,
								`updated_at` datetime(6) DEFAULT NULL,
								PRIMARY KEY (`aggregate_id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS  `performance__handler` (
										`id` varchar(255) NOT NULL,
										`action` varchar(255) DEFAULT NULL,
										`bundle` varchar(255) DEFAULT NULL,
										`component` varchar(255) DEFAULT NULL,
										`last_service_time` double NOT NULL,
										`mean_service_time` double NOT NULL,
										PRIMARY KEY (`id`)
) ENGINE=InnoDB ;







