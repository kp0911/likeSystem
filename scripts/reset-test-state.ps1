docker exec mariadb-container mariadb -u db_user -pdb_password like_system -e "INSERT INTO video (id, like_count) VALUES (1, 0) ON DUPLICATE KEY UPDATE like_count = 0;"
docker exec redis-container redis-cli FLUSHDB
docker exec rabbitmq-container rabbitmqctl purge_queue like.queue
docker exec rabbitmq-container rabbitmqctl purge_queue like.aggregate.queue
