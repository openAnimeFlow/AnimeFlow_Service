-- 月度归档按 request_time、id 游标顺序分批读取。
ALTER TABLE `api_access_log`
  ADD INDEX `idx_request_time_id` (`request_time` ASC, `id` ASC);
