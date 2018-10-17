create table CARRIERS
(
	cid varchar(7) not null
		primary key,
	name varchar(83)
)

create table MONTHS
(
	mid int not null
		primary key,
	month varchar(9)
)

create table WEEKDAYS
(
	did int not null
		primary key,
	day_of_week varchar(9)
)

create table FLIGHTS
(
	fid int not null
		primary key,
	month_id int
		constraint FLIGHTS_MONTHS_mid_fk
		references MONTHS,
	day_of_month int,
	dayof_week_id int
		constraint FLIGHTS_WEEKDAYS_did_fk
		references WEEKDAYS,
	carrier_id varchar(7)
		constraint FLIGHTS_CARRIERS_cid_fk
		references CARRIERS,
	flight_num int,
	origin_city varchar(34),
	origin_state varchar(47),
	dest_city varchar(34),
	dest_state varchar(46),
	departure_delay int,
	taxi_out int,
	arrival_delay int,
	canceled int,
	actual_time int,
	distance int,
	capacity int,
	price int
)

create table CUSTOMERS
(
	customer_id int identity
		primary key,
	username varchar(20) not null,
	pwd varchar(20) not null,
	initAmount int
)

create unique index CUSTOMERS_username_uindex
	on CUSTOMERS (username)


create table ITINERARIES
(
	iid int identity
		primary key,
	customer_id int not null
		constraint ITINERARIES_CUSTOMERS_customer_id_fk
		references CUSTOMERS,
	paid int not null,
	canceled int not null
)

create table FLIGHT_ITINERARY
(
	fiid int identity
		primary key,
	fid int not null
		constraint FLIGHT_ITINERARY_FLIGHTS_fid_fk
		references FLIGHTS,
	iid int not null
		constraint FLIGHT_ITINERARY_ITINERARIES_iid_fk
		references ITINERARIES
)
