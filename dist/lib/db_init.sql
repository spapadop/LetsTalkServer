create database swatchat2;
use swatchat2;
create table users (
user_id int not null auto_increment,
username varchar(30) not null,
pwd varchar(30) not null,
email varchar(30) not null,
admin_flag int,
status int,
primary key (user_id));

create table address(
user_id int not null,
user_id_has int not null,
foreign key (user_id) references users (user_id) ON DELETE CASCADE ON UPDATE CASCADE);
alter table address add foreign key (user_id_has) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE;

create table user_activity(
user_id int not null,
time_in timestamp not null,
time_out timestamp not null
);
alter table address add foreign key (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE;

create table conversations (
conv_id int not null auto_increment,
user_one int ,
user_two int ,
chat_type int not null,
time_of_update timestamp not null,
time_user_one timestamp not null,
time_user_two timestamp not null,
status int ,
primary key(conv_id)
);
alter table conversations add foreign key (user_one) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE;
 

create table text_msg (
text_id int not null auto_increment,
text_msg varchar(250) not null,
primary key(text_id));


create table block_list (
user_id_by int not null,
user_id_to int not null,
time timestamp not null );

alter table block_list add foreign key (user_id_by) references users(user_id) on delete cascade on update cascade;
alter table block_list add foreign key (user_id_to) references users(user_id) on delete cascade on update cascade; 

 
create table files(
file_id int not null auto_increment,
filename varchar(100) not null,
primary key(file_id));

create table file_repo(
file_id int not null auto_increment,
filename varchar(100) not null,
primary key(file_id));


create table conv_msg(
conv_msg_id int not null auto_increment,
conv_id int not null,
user_id_s int not null,
user_id_r int ,
text_id int,
file_id int,
msg_type varchar(10) not null,
time timestamp not null ,
user_s_ip varchar(20) not null,
user_r_ip varchar(20) ,
primary key(conv_msg_id)
);
alter table conv_msg add foreign key (conv_id) references conversations(conv_id) on delete cascade on update cascade ;
alter table conv_msg add foreign key (user_id_s) references users(user_id) on delete cascade on update cascade ;


create table group_users(
conv_id int not null,
g_name varchar(30) ,
user_id int not null,
status varchar(20) not null,
time_user timestamp);
alter table group_users add foreign key (conv_id) references conversations(conv_id) on delete cascade on update cascade ;
alter table group_users add foreign key (user_id) references users(user_id) on delete cascade on update cascade ;

