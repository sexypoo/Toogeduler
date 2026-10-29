-- 초대 링크 만료 (#21). 기존 그룹의 링크는 배포 시점부터 7일 동안 쓸 수 있게 채운다.
alter table calendar_groups add column invite_expires_at timestamp(6) with time zone;

update calendar_groups
set invite_expires_at = current_timestamp + interval '7' day
where invite_expires_at is null;
