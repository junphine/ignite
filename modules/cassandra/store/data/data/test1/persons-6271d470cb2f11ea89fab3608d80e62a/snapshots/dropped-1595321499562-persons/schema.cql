CREATE TABLE IF NOT EXISTS test1.persons (
	company text,
	department text,
	number bigint,
	age smallint,
	birth_date timestamp,
	first_name text,
	fullname text,
	height bigint,
	last_name text,
	married boolean,
	phones blob,
	weight float,
	PRIMARY KEY ((company, department), number))
	WITH ID = 6271d470-cb2f-11ea-89fa-b3608d80e62a
	AND CLUSTERING ORDER BY (number DESC)
	AND bloom_filter_fp_chance = 0.01
	AND dclocal_read_repair_chance = 0.1
	AND crc_check_chance = 1.0
	AND default_time_to_live = 0
	AND gc_grace_seconds = 864000
	AND min_index_interval = 128
	AND max_index_interval = 2048
	AND memtable_flush_period_in_ms = 0
	AND read_repair_chance = 0.2
	AND speculative_retry = '99PERCENTILE'
	AND comment = 'A most excellent and useful table'
	AND caching = { 'keys': 'ALL', 'rows_per_partition': 'NONE' }
	AND compaction = { 'max_threshold': '32', 'min_threshold': '4', 'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy' }
	AND compression = { 'chunk_length_in_kb': '64', 'class': 'org.apache.cassandra.io.compress.LZ4Compressor' }
	AND cdc = false
	AND extensions = {  };
CREATE INDEX persons_married_idx ON test1.persons (married);
