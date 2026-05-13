INSERT INTO video (id, like_count) VALUES (1, 0) ON DUPLICATE KEY UPDATE id=id;
