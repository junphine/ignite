CREATE TABLE IF NOT EXISTS test1.pojo_test5 (
	company_code text,
	department_code text,
	person_num bigint,
	age smallint,
	birth_date timestamp,
	first_name text,
	height bigint,
	last_name text,
	married boolean,
	phones blob,
	weight float,
	PRIMARY KEY ((company_code, department_code), person_num))
	WITH ID = 62dfff90-cb2f-11ea-89fa-b3608d80e62a
	AND CLUSTERING ORDER BY (person_num ASC)
	AND bloom_filter_fp_chance = 0.01
	AND dclocal_read_repair_chance = 0.1
	AND crc_check_chance = 1.0
	AND default_time_to_live = 0
	AND gc_grace_seconds = 864000
	AND min_index_interval = 128
	AND max_index_interval = 2048
	AND memtable_flush_period_in_ms = 0
	AND read_repair_chance = 0.0
	AND speculative_retry = '99PERCENTILE'
	AND comment = ''
	AND caching = { 'keys': 'ALL', 'rows_per_partition': 'NONE' }
	AND compaction = { 'max_threshold': '32', 'min_threshold': '4', 'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy' }
	AND compression = { 'chunk_length_in_kb': '64', 'class': 'org.apache.cassandra.io.compress.LZ4Compressor' }
	AND cdc = false
	AND extensions = {  };
CREATE INDEX pojo_test5_married_idx ON test1.pojo_test5 (married);
