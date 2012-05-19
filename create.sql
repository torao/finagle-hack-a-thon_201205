create database whiteboard;

create table events(
	id         bigint not null autoincrement,
	message    text not null,
	created_at timestamp
);