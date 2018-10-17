import java.io.FileInputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * Runs queries against a back-end database
 */
@SuppressWarnings({"WeakerAccess", "FieldCanBeLocal"})
public class Query {

	// JDBC drivers
	private String configFilename;
	private Properties configProps = new Properties();

	private String jSQLDriver;
	private String jSQLUrl;
	private String jSQLUser;
	private String jSQLPassword;

	// DB Connection
	private Connection conn;
	private static final int RETRIES = 2;

	// Logged In User
	private String username; // customer username is unique
	private int customer_id;

	// Canned queries
	private static final String CHECK_FLIGHT_CAPACITY =
			"SELECT capacity FROM Flights WHERE fid = ?";
	private PreparedStatement checkFlightCapacityStatement;

	// TODO: refactor queries

	// USER table
	private static final String LOGIN_STATEMENT =
			"SELECT * FROM CUSTOMERS WHERE username = ? AND pwd = ?";
	private PreparedStatement login;

	private static final String CHECK_USER =
			"select count(*) as n from CUSTOMERS where username = ?";
	private PreparedStatement checkUser;

	private static final String GET_USER =
			"select customer_id, initAmount from CUSTOMERS where username = ?";
	private PreparedStatement getUser;

	private static final String NEW_USER =
			"insert into CUSTOMERS (username, pwd, initAmount) " +
					"VALUES (?,?,?)";
	private PreparedStatement createNewUser;

	private static final String UPDATE_BALANCE =
			"update CUSTOMERS set initAmount = initAmount - ? where username = ?";
	private PreparedStatement updateBalance;

	private static final String UPDATE_RESERVATION =
			"update ITINERARIES set paid = 1 where iid = ?";
	private PreparedStatement updateReservation;

	private static final String CANCEL_RESERVATION =
			"update ITINERARIES set canceled = 1 where iid = ?";
	private PreparedStatement cancelReservation;

	// FLIGHT table
	private static final String DIRECT_SEARCH =
			"SELECT TOP (?) fid,day_of_month,carrier_id,flight_num,origin_city,dest_city,actual_time,capacity,price " +
					"FROM Flights " +
					"WHERE origin_city = ? AND " +
					"dest_city = ? AND " +
					"day_of_month = ? and " +
					"canceled = 0 " +
					"ORDER BY actual_time, fid ASC";
	private PreparedStatement directSearch;

	private static final String INDIRECT_SEARCH =
			/*
			 * params:
			 * 1, top
			 * 2, f1.origin_city
			 * 3, f2.dest_city
			 * 4, f1.day_of_month
			 * 5, f2.day_of_month
			 */
			"select top (?) " +
					"f1.fid fid1, f1.day_of_month day_of_month1, f1.carrier_id carrier_id1, f1.flight_num flight_num1, f1.origin_city origin_city1, f1.dest_city dest_city1, f1.actual_time actual_time1, f1.capacity capacity1, f1.price price1, " +
					"f2.fid fid2, f2.day_of_month day_of_month2, f2.carrier_id carrier_id2, f2.flight_num flight_num2, f2.origin_city origin_city2, f2.dest_city dest_city2, f2.actual_time actual_time2, f2.capacity capacity2, f2.price price2 " +
					"from FLIGHTS f1, FLIGHTS f2 " +
					"where f1.origin_city = ? and " +
					"f1.dest_city = f2.origin_city and " +
					"f2.dest_city = ? and " +
					"f1.day_of_month = ? and " +
					"f2.day_of_month = ? and " +
					"f1.canceled = 0 and " +
					"f2.canceled = 0 " +
					"order by f1.actual_time + f2.actual_time, fid1, fid2 asc";
	private PreparedStatement indirectSearch;

	private static final String OCCUPIED_SEATS =
			"select count(*) n from FLIGHT_ITINERARY fi, ITINERARIES i " +
					"where fi.fid = ? and " +
					"i.iid = fi.iid and " +
					"i.canceled = 0";
	private PreparedStatement occupiedSeats;

	// ITINERARY table
	private static final String RESERVATION_COUNT =
			"select count(*) n from CUSTOMERS c, ITINERARIES i " +
					"where c.username = ? and " +
					"c.customer_id = i.customer_id and " +
					"i.canceled = 0";
	private PreparedStatement reservationCount;

	private static final String GET_RESERVATIONS =
			"select iid, c.customer_id, paid, canceled from CUSTOMERS c, ITINERARIES i " +
					"where c.username = ? and " +
					"c.customer_id = i.customer_id and " +
					"i.canceled = 0";
	private PreparedStatement getReservations;

	private static final String ITINERARY_FLIGHTS =
			"select f.fid fid, day_of_month, carrier_id, flight_num, origin_city, dest_city, actual_time, capacity, price " +
					"from FLIGHT_ITINERARY fi, FLIGHTS f, ITINERARIES i " +
					"where fi.iid = ? and " +
					"f.fid = fi.fid and " +
					"i.iid = fi.iid and " +
					"i.canceled = 0";
	private PreparedStatement reservedFlights;

	private static final String NEW_ITINERARY =
			"insert into ITINERARIES (customer_id, paid, canceled) " +
					"VALUES (?,0,0)";
	private PreparedStatement createNewReservation;

	private static final String NEW_F_I =
			"insert into FLIGHT_ITINERARY (fid, iid) " +
					"VALUES (?,?)";
	private PreparedStatement createNewFI;

	private static final String NEW_PK =
			"select @@identity newPK";
	private PreparedStatement newPK;

	private static final String DAYS_ALREADY_BOOKED =
			"select distinct f.day_of_month day " +
					"from CUSTOMERS c, ITINERARIES i, FLIGHT_ITINERARY fi, FLIGHTS f " +
					"where c.username = ? and " +
					"c.customer_id = i.customer_id and " +
					"i.iid = fi.iid and " +
					"f.fid = fi.fid and " +
					"i.canceled = 0";
	private PreparedStatement dayAlreadyBooked;

	// Transactions
	private static final String BEGIN_TRANSACTION_SQL =
			"SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; " +
					"BEGIN TRANSACTION;";
	private PreparedStatement beginTransactionStatement;

	private static final String COMMIT_SQL = "COMMIT TRANSACTION";
	private PreparedStatement commitTransactionStatement;

	private static final String ROLLBACK_SQL = "ROLLBACK TRANSACTION";
	private PreparedStatement rollbackTransactionStatement;

	// Clear tables
	private static final String CLEAR_TABLES =
			"delete from FLIGHT_ITINERARY;" +
					"DBCC CHECKIDENT (FLIGHT_ITINERARY, RESEED, 0);" +
					"delete from ITINERARIES;" +
					"DBCC CHECKIDENT (ITINERARIES, RESEED, 0);" +
					"delete from CUSTOMERS;" +
					"DBCC CHECKIDENT (CUSTOMERS, RESEED, 0);";
	private PreparedStatement clearTables;

	// Search result for current session
	private List<Flight> searchHistory;
	// Reservations for current session
//	private List<Flight> reservationHistory;

	public Query(String configFilename) {
		this.configFilename = configFilename;
	}

	/* Connection code to SQL Azure.  */
	public void openConnection() throws Exception {
		configProps.load(new FileInputStream(configFilename));

		jSQLDriver = configProps.getProperty("flightservice.jdbc_driver");
		jSQLUrl = configProps.getProperty("flightservice.url");
		jSQLUser = configProps.getProperty("flightservice.sqlazure_username");
		jSQLPassword = configProps.getProperty("flightservice.sqlazure_password");

		/* load jdbc drivers */
		Class.forName(jSQLDriver).newInstance();

		/* open connections to the flights database */
		conn = DriverManager.getConnection(jSQLUrl, // database
				jSQLUser,                           // user
				jSQLPassword);                      // password

		conn.setAutoCommit(true); // by default automatically commit after each statement
		conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

    /* You will also want to appropriately set the transaction's isolation level through:
       conn.setTransactionIsolation(...)
       See Connection class' JavaDoc for details.
    */
	}

	public void closeConnection() throws Exception {
		conn.close();
	}

	/**
	 * Clear the data in any custom tables created. Do not drop any tables and do not
	 * clear the flights table. You should clear any tables you use to store getReservations
	 * and reset the next reservation ID to be 1.
	 */
	public void clearTables() throws Exception {
		clearTables.executeUpdate();
		clearTables.close();
	}

	/**
	 * Prepare all the SQL statements in this method.
	 */
	public void prepareStatements() throws Exception {
		beginTransactionStatement = conn.prepareStatement(BEGIN_TRANSACTION_SQL);
		commitTransactionStatement = conn.prepareStatement(COMMIT_SQL);
		rollbackTransactionStatement = conn.prepareStatement(ROLLBACK_SQL);

		checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);

		// USER table
		checkUser = conn.prepareStatement(CHECK_USER);
		getUser = conn.prepareStatement(GET_USER);
		login = conn.prepareStatement(LOGIN_STATEMENT);
		createNewUser = conn.prepareStatement(NEW_USER);

		// Search
		directSearch = conn.prepareStatement(DIRECT_SEARCH);
		indirectSearch = conn.prepareStatement(INDIRECT_SEARCH);
		occupiedSeats = conn.prepareStatement(OCCUPIED_SEATS);

		// ITINERARY table
		createNewReservation = conn.prepareStatement(NEW_ITINERARY);
		reservedFlights = conn.prepareStatement(ITINERARY_FLIGHTS);
		reservationCount = conn.prepareStatement(RESERVATION_COUNT);
		newPK = conn.prepareStatement(NEW_PK);
		createNewFI = conn.prepareStatement(NEW_F_I);
		dayAlreadyBooked = conn.prepareStatement(DAYS_ALREADY_BOOKED);
		getReservations = conn.prepareStatement(GET_RESERVATIONS);
		updateBalance = conn.prepareStatement(UPDATE_BALANCE);
		updateReservation = conn.prepareStatement(UPDATE_RESERVATION);
		cancelReservation = conn.prepareStatement(CANCEL_RESERVATION);

		// Clear tables
		clearTables = conn.prepareStatement(CLEAR_TABLES);
	}

	/**
	 * Takes a user's username and password and attempts to log the user in.
	 *
	 * @param username Username
	 * @param password Password
	 * @return If someone has already logged in, then return "User already logged in\n"
	 * For all other errors, return "Login failed\n".
	 * <p>
	 * Otherwise, return "Logged in as [username]\n".
	 * @see <a href="https://docs.oracle.com/javase/tutorial/jdbc/basics/processingsqlstatements.html">
	 * Processing SQL Statements with JDBC</a>
	 * @see <a href="https://docs.oracle.com/javase/tutorial/jdbc/basics/prepared.html">
	 * Using Prepared Statements</a>
	 */

	public String transaction_login(String username, String password) throws IllegalArgumentException {

		if (loggedIn()) {
			return "User already logged in\n";
		}

		if (username.length() > 20 || password.length() > 20) {
			return "Login failed\n";
		}

		try {

			prepareStatements();
			beginTransaction();

			if (checkUser(username)) {
				rollbackTransaction();
				return "Login failed\n";
			}

			login.clearParameters();
			login.setString(1, username);
			login.setString(2, password);

			ResultSet q = login.executeQuery();

			q.next();
			String result_username = q.getString("username");
			String result_pwd = q.getString("pwd");
			this.customer_id = q.getInt("customer_id");

			login.close();

			if (result_username == null || result_pwd == null || !result_pwd.equals(password)) {
				rollbackTransaction();
				return "Login failed\n";
			} else {
				this.username = username;
				commitTransaction();
				return "Logged in as " + this.username + "\n";
			}

		} catch (Exception e) {

			try {
				rollbackTransaction();
			} catch (Exception ignored) {
			}

			e.printStackTrace();
			return "Login failed\n";
		}
	}

	/**
	 * Implement the create user function.
	 *
	 * @param username   new user's username. User names are unique the system.
	 * @param password   new user's password.
	 * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure otherwise).
	 * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
	 */
	public String transaction_createCustomer(String username, String password, int initAmount) {

		if (initAmount < 0 || username.length() > 20 || password.length() > 20) {
			return "Failed to create user\n";
		}

		boolean deadlocked = false;
		int retry = 0;

		do {

			try {

				prepareStatements();
//			beginTransaction();

//			boolean existing = checkUser(username);

				// username not existing
				if (checkUser(username)) {

					createNewUser.clearParameters();
					createNewUser.setString(1, username);
					createNewUser.setString(2, password);
					createNewUser.setInt(3, initAmount);

					createNewUser.executeUpdate();

					ResultSet newCID = this.newPK.executeQuery();
					newCID.next();
					this.customer_id = newCID.getInt("newPK");

					createNewUser.close();
//				commitTransaction();

					return "Created user " + username + "\n";

				} else {
//				rollbackTransaction();
					return "Failed to create user\n";
				}
			} catch (SQLException e) {
				deadlocked = true;
			} catch (Exception e) {
				// something actually gone wrong
				try { rollbackTransaction(); } catch (Exception ignored) {}

				e.printStackTrace();
				return ("Failed to create user\n");
			}
		} while (deadlocked && retry++ < RETRIES);

		return "Failed to create user\n";
	}

	/**
	 * Implement the search function.
	 * <p>
	 * Searches for flights from the given origin city to the given destination
	 * city, on the given day of the month. If {@code directFlight} is true, it only
	 * searches for direct flights, otherwise is searches for direct flights
	 * and flights with two "hops." Only searches for up to the number of
	 * itineraries given by {@code numberOfItineraries}.
	 * <p>
	 * The results are sorted based on total flight time.
	 *
	 * @param originCity          Origin city
	 * @param destinationCity     Destination city
	 * @param directFlight        If true, then only search for direct flights, otherwise include indirect flights as well
	 * @param dayOfMonth          Day of month
	 * @param numberOfItineraries Number of itineraries to return
	 * @return If no itineraries were found, return "No flights match your selection\n".
	 * If an error occurs, then return "Failed to search\n".
	 * <p>
	 * Otherwise, the sorted itineraries printed in the following format:
	 * <p>
	 * Itinerary [itinerary number]: [number of flights] flight(s), [total flight time] minutes\n
	 * [first flight in itinerary]\n
	 * ...
	 * [last flight in itinerary]\n
	 * <p>
	 * Each flight should be printed using the same format as in the {@code Flight} class. Itinerary numbers
	 * in each search should always start from 0 and increase by 1.
	 * @see DirectFlight#toString()
	 */
	public String transaction_search(String originCity, String destinationCity, boolean directFlight, int dayOfMonth,
	                                 int numberOfItineraries) {

		StringBuilder sb = new StringBuilder();

		if (this.searchHistory == null) {
			this.searchHistory = new ArrayList<>();
		} else {
			this.searchHistory.clear(); // refreshes session's search history
		}

		List<DirectFlight> directSearch = new ArrayList<>();
		List<IndirectFlight> indirectSearch = new ArrayList<>();

		try {

			prepareStatements();
			beginTransaction();

			setDirectSearch(originCity, destinationCity, dayOfMonth, numberOfItineraries, directSearch);

			if (!directFlight) {
				setIndirectSearch(originCity, destinationCity, dayOfMonth, numberOfItineraries, indirectSearch);
			}

			int numDirectResults = directSearch.size();
			int numIndirectResults = indirectSearch.size();

			if (numDirectResults == 0 && numIndirectResults == 0) {
				rollbackTransaction();
				return "No flights match your selection\n";
			}

			directSearch.sort(Comparator.comparingInt(Flight::compareTo));
			indirectSearch.sort(Comparator.comparingInt(Flight::compareTo));

			// Populating search results
			for (int i = 0; i < numberOfItineraries; i++) {
				if (i < directSearch.size()) {
					this.searchHistory.add(directSearch.get(i));
				}
			}

			for (int i = 0; i < numberOfItineraries - this.searchHistory.size(); i++) {
				if (i < indirectSearch.size()) {
					this.searchHistory.add(indirectSearch.get(i));
				}
			}

			this.searchHistory.sort(Comparator.comparingInt(Flight::compareTo));

			for (int i = 0; i < numberOfItineraries; i++) {

				if (i < this.searchHistory.size()) {
					int flightType = this.searchHistory.get(i).getFlightType();
					int totalTime = this.searchHistory.get(i).getTime();
					sb.append("Itinerary ").append(i).append(": ").append(flightType).append(" flight(s), ").append(totalTime).append(" minutes\n");
					sb.append(this.searchHistory.get(i).toString());
				}
			}

			commitTransaction();
			return sb.toString();

		} catch (Exception e) {

			try {
				rollbackTransaction();
			} catch (Exception ignored) {
			}

			e.printStackTrace();
			return "Failed to search\n";
		}
	}

	/**
	 * Implements the book itinerary function.
	 *
	 * @param itineraryId ID of the itinerary to book. This must be one that is returned by search in the current session.
	 * @return If the user is not logged in, then return "Cannot book reservations, not logged in\n".
	 * If try to book an itinerary with invalid ID, then return "No such itinerary {@code itineraryId}\n".
	 * If the user already has a reservation on the same day as the one that they are trying to book now, then return
	 * "You cannot book two flights in the same day\n".
	 * For all other errors, return "Booking failed\n".
	 * <p>
	 * And if booking succeeded, return "Booked flight(s), reservation ID: [reservationId]\n" where
	 * reservationId is a unique number in the reservation system that starts from 1 and increments by 1 each time a
	 * successful reservation is made by any user in the system.
	 */
	public String transaction_book(int itineraryId) {

		if (!loggedIn()) {
			return "Cannot book reservations, not logged in\n";
		}

		if (this.searchHistory.isEmpty() || itineraryId + 1 > this.searchHistory.size()) {
			return "No such itinerary " + itineraryId + "\n";
		}

		boolean deadlocked = false;
		int retry = 0;

		do {

			// the new itinerary created
			int iid;

			try {

				prepareStatements();
				beginTransaction();

				int flightType = this.searchHistory.get(itineraryId).getFlightType();

				/* check for fail same-day flights */
				int dayBooked;
				if (flightType == 1) {
					DirectFlight f = (DirectFlight) this.searchHistory.get(itineraryId);
					dayBooked = f.getDayOfMonth();
				} else {
					IndirectFlight f = (IndirectFlight) this.searchHistory.get(itineraryId);
					dayBooked = f.getFlight1().getDayOfMonth();
				}

				dayAlreadyBooked.clearParameters();
				dayAlreadyBooked.setString(1, this.username);
				ResultSet days = dayAlreadyBooked.executeQuery();

				while (days.next()) {
					if (dayBooked == days.getInt("day")) {
						rollbackTransaction();
						return "You cannot book two flights in the same day\n";
					}
				}
				dayAlreadyBooked.close();

				/* check availability */
				if (flightType == 1) {
					// Direct flight
					DirectFlight f = (DirectFlight) this.searchHistory.get(itineraryId);
					int fid = f.getFid();

					if (checkNumberOfSeatsReserved(fid) >= checkFlightCapacity(fid)) {
						rollbackTransaction();
						return "Booking failed\n";
					}

					/* Actually books the flight */
					iid = bookDirectFlight(fid);
				} else {
					// Indirect flight
					DirectFlight f1 = ((IndirectFlight) this.searchHistory.get(itineraryId)).getFlight1();
					int fid1 = f1.getFid();
					DirectFlight f2 = ((IndirectFlight) this.searchHistory.get(itineraryId)).getFlight2();
					int fid2 = f2.getFid();

					if (checkNumberOfSeatsReserved(fid1) >= checkFlightCapacity(fid1) &&
							checkNumberOfSeatsReserved(fid2) >= checkFlightCapacity(fid2)) {
						rollbackTransaction();
						return "Booking failed\n";
					}

					/* Actually books the flight */
					iid = bookIndirectFlight(fid1, fid2);
				}

				commitTransaction();
				return "Booked flight(s), reservation ID: " + iid + "\n";

			} catch (SQLException e) {
				// deadlocked
				deadlocked = true;
			} catch (Exception e) {
				// something actually gone wrong
				try { rollbackTransaction(); } catch (Exception ignored) {}

				e.printStackTrace();
				return "Booking failed\n";
			}

		} while (deadlocked && retry++ < RETRIES);

		return "Booking failed\n";
	}

	/**
	 * Implements the reservations function.
	 *
	 * @return If no user has logged in, then return "Cannot view reservations, not logged in\n"
	 * If the user has no reservations, then return "No reservations found\n"
	 * For all other errors, return "Failed to retrieve reservations\n"
	 * <p>
	 * Otherwise return the reservations in the following format:
	 * <p>
	 * Reservation [reservation ID] paid: [true or false]:\n"
	 * [flight 1 under the reservation]
	 * [flight 2 under the reservation]
	 * Reservation [reservation ID] paid: [true or false]:\n"
	 * [flight 1 under the reservation]
	 * [flight 2 under the reservation]
	 * ...
	 * <p>
	 * Each flight should be printed using the same format as in the {@code Flight} class.
	 * @see DirectFlight#toString()
	 */
	public String transaction_reservations() {

		if (!loggedIn()) {
			return "Cannot view reservations, not logged in\n";
		}

		StringBuilder sb = new StringBuilder();

		try {
			prepareStatements();
			beginTransaction();

			// Check if has getReservations at all
			if (checkNumberOfReservations() == 0) {
				rollbackTransaction();
				return "No reservations found\n";
			}

			getReservations.clearParameters();
			getReservations.setString(1, this.username);
			ResultSet rsvp = this.getReservations.executeQuery();

			while (rsvp.next()) {
				int iid = rsvp.getInt("iid");

				sb.append("Reservation ").append(iid).append(" paid: ")
						.append(rsvp.getInt("paid") == 1).append(":").append("\n");

				reservedFlights.clearParameters();
				reservedFlights.setInt(1, iid);
				ResultSet results = reservedFlights.executeQuery();

				while (results.next()) {
					int fid = results.getInt("fid");
					int dayOfMonth = results.getInt("day_of_month");
					String carrierId = results.getString("carrier_id");
					String flightNum = results.getString("flight_num");
					String originCity = results.getString("origin_city");
					String destCity = results.getString("dest_city");
					int time = results.getInt("actual_time");
					int capacity = results.getInt("capacity");
					int price = results.getInt("price");


					sb.append("ID: ").append(fid)
							.append(" Day: ").append(dayOfMonth)
							.append(" Carrier: ").append(carrierId)
							.append(" Number: ").append(flightNum)
							.append(" Origin: ").append(originCity)
							.append(" Dest: ").append(destCity)
							.append(" Duration: ").append(time)
							.append(" Capacity: ").append(capacity)
							.append(" Price: ").append(price).append("\n");
				}
				reservedFlights.close();
			}
			rsvp.close();
			getReservations.close();
			commitTransaction();

		} catch (Exception e) {

			try {
				rollbackTransaction();
			} catch (Exception ignored) {
			}

			e.printStackTrace();
			return "Failed to retrieve reservations\n";
		}


		return sb.toString();
	}


	/**
	 * Implements the cancel operation.
	 *
	 * @param reservationId the reservation ID to cancel
	 * @return If no user has logged in, then return "Cannot cancel getReservations, not logged in\n"
	 * For all other errors, return "Failed to cancel reservation [reservationId]"
	 * <p>
	 * If successful, return "Canceled reservation [reservationId]"
	 * <p>
	 * Even though a reservation has been canceled, its ID should not be reused by the system.
	 */
	public String transaction_cancel(int reservationId) {

		if (!loggedIn()) {
			return "Cannot cancel getReservations, not logged in\n";
		}

		try {
			prepareStatements();
			beginTransaction();

			reservationCount.clearParameters();
			reservationCount.setString(1, this.username);
			ResultSet result = reservationCount.executeQuery();
			result.next();
			int rsvpCount = result.getInt("n");
			reservationCount.close();

			if (rsvpCount == 0) {
				rollbackTransaction();
				return "Failed to cancel reservation " + reservationId + "\n";
			}

			cancelReservation.clearParameters();
			cancelReservation.setInt(1, reservationId);
			cancelReservation.executeUpdate();
			cancelReservation.close();

			commitTransaction();

			return "Canceled reservation " + reservationId + "\n";
		} catch (Exception e) {

			try {
				rollbackTransaction();
			} catch (Exception ignored) {
			}

			e.printStackTrace();
			return "Failed to cancel reservation " + reservationId + "\n";
		}

	}

	/**
	 * Implements the pay function.
	 *
	 * @param reservationId the reservation to pay for.
	 * @return If no user has logged in, then return "Cannot pay, not logged in\n"
	 * If the reservation is not found / not under the logged in user's name, then return
	 * "Cannot find unpaid reservation [reservationId] under user: [username]\n"
	 * If the user does not have enough money in their account, then return
	 * "User has only [balance] in account but itinerary costs [cost]\n"
	 * For all other errors, return "Failed to pay for reservation [reservationId]\n"
	 * <p>
	 * If successful, return "Paid reservation: [reservationId] remaining balance: [balance]\n"
	 * where [balance] is the remaining balance in the user's account.
	 */
	public String transaction_pay(int reservationId) {

		if (!loggedIn()) {
			return "Cannot pay, not logged in\n";
		}

		try {

			prepareStatements();
			beginTransaction();

			if (!ifReservationExists(reservationId)) {
				rollbackTransaction();
				return "Cannot find unpaid reservation " + reservationId + " under user: " + this.username + "\n";
			}

			// get reservation, calculate price
			int totalPrice = totalPrice(reservationId);

			// get balance
			int availableBalance = availableBalance();
			if (availableBalance <= totalPrice) {
				rollbackTransaction();
				return "User has only " + availableBalance + " in account but itinerary costs " + totalPrice + "\n";
			}

			// update balance
			updateBalance.setInt(1, totalPrice);
			updateBalance.setString(2, this.username);
			updateBalance.executeUpdate();
			updateBalance.close();

			// update payment status
			updateReservation.setInt(1, reservationId);
			updateReservation.executeUpdate();
			updateReservation.close();

			commitTransaction();
			return "Paid reservation: " + reservationId + " remaining balance: " + (availableBalance - totalPrice) + "\n";

		} catch (Exception e) {

			try {
				rollbackTransaction();
			} catch (Exception ignored) {
			}

			e.printStackTrace();
			return "Failed to pay for reservation " + reservationId + "\n";
		}
	}

	/* Utility functions  */

	public void beginTransaction() throws Exception {
		conn.setAutoCommit(false);
		beginTransactionStatement.executeUpdate();
	}

	public void commitTransaction() throws Exception {
		commitTransactionStatement.executeUpdate();
		conn.setAutoCommit(true);
	}

	public void rollbackTransaction() throws Exception {
		rollbackTransactionStatement.executeUpdate();
		conn.setAutoCommit(true);
	}

	/**
	 * Shows an example of using PreparedStatements after setting arguments. You don't need to
	 * use this method if you don't want to.
	 */
	private int checkFlightCapacity(int fid) throws Exception {
		prepareStatements();

		checkFlightCapacityStatement.clearParameters();
		checkFlightCapacityStatement.setInt(1, fid);
		ResultSet results = checkFlightCapacityStatement.executeQuery();
		results.next();
		int capacity = results.getInt("capacity");
		results.close();

		return capacity;
	}

	/**
	 * Checks the number of reservation that the current session user has
	 *
	 * @return Number of reservations
	 * @throws Exception Everything
	 */
	private int checkNumberOfReservations() throws Exception {
		prepareStatements();

		reservationCount.clearParameters();
		reservationCount.setString(1, this.username);
		ResultSet cnt = reservationCount.executeQuery();
		cnt.next();
		int rsvpCount = cnt.getInt("n");
		reservationCount.close();

		return rsvpCount;
	}

	/**
	 * Checks the number of seats available for a specific flight
	 *
	 * @param fid Flight ID
	 * @return Number of seats available
	 * @throws Exception Everything
	 */
	private int checkNumberOfSeatsReserved(int fid) throws Exception {
		prepareStatements();

		occupiedSeats.clearParameters();
		occupiedSeats.setInt(1, fid);
		ResultSet results = occupiedSeats.executeQuery();
		results.next();
		int seatsReserved = results.getInt("n");
		occupiedSeats.close();

		return seatsReserved;
	}

	/**
	 * Books a direct flight itinerary and creates a new record in Itinerary table accordingly
	 *
	 * @param fid Flight ID
	 * @return New itinerary ID gets created
	 * @throws Exception Everything
	 */
	private int bookDirectFlight(int fid) throws Exception {
		prepareStatements();

		// create new itinerary
		createNewReservation.clearParameters();
		createNewReservation.setInt(1, this.customer_id);
		createNewReservation.executeUpdate();
		createNewReservation.close();

		// get new iid
		ResultSet results = newPK.executeQuery();
		results.next();
		int iid = results.getInt("newPK");
		newPK.close();

		// create new itinerary_flights
		createNewFI.clearParameters();
		createNewFI.setInt(1, fid);
		createNewFI.setInt(2, iid);
		createNewFI.executeUpdate();
		createNewFI.close();

		return iid;
	}

	/**
	 * Books indirect flight itinerary
	 *
	 * @param fid1 Flight 1 of the indirect flights
	 * @param fid2 Flight 2
	 * @return New itinerary ID gets created
	 * @throws Exception Everything
	 */
	private int bookIndirectFlight(int fid1, int fid2) throws Exception {
		prepareStatements();

		// create new itinerary
		createNewReservation.clearParameters();
		createNewReservation.setInt(1, this.customer_id);
		createNewReservation.executeUpdate();
		createNewReservation.close();

		// get new iid
		ResultSet results = newPK.executeQuery();
		results.next();
		int iid = results.getInt("newPK");
		newPK.close();

		// create new itinerary_flights
		createNewFI.clearParameters();
		createNewFI.setInt(1, fid1);
		createNewFI.setInt(2, iid);
		createNewFI.executeUpdate();
		createNewFI.close();

		createNewFI.clearParameters();
		createNewFI.setInt(1, fid2);
		createNewFI.setInt(2, iid);
		createNewFI.executeUpdate();
		createNewFI.close();

		return iid;
	}

	/**
	 * Check if the requested reservation exists
	 *
	 * @param iid Itinerary ID to be searched
	 * @return True if exists; false if otherwise
	 * @throws Exception Everything
	 */
	private boolean ifReservationExists(int iid) throws Exception {
		prepareStatements();

		getReservations.clearParameters();
		getReservations.setString(1, this.username);
		ResultSet results = this.getReservations.executeQuery();

		// check if reservation exists
		while (results.next()) {
			if (results.getInt("iid") == iid && results.getInt("paid") == 0) {
				getReservations.close();
				return true;
			}
		}
		getReservations.close();

		return false;
	}

	/**
	 * Calculates the total price of an itinerary
	 *
	 * @param iid Itinerary ID
	 * @return Total price of the itinerary
	 * @throws Exception Everything
	 */
	private int totalPrice(int iid) throws Exception {
		prepareStatements();

		reservedFlights.clearParameters();
		reservedFlights.setInt(1, iid);
		ResultSet flights = reservedFlights.executeQuery();
		int totalPrice = 0;

		while (flights.next()) {
			totalPrice += flights.getInt("price");
		}
		reservedFlights.close();

		return totalPrice;
	}

	/**
	 * Retrieves the amount of remaining funds in the account
	 *
	 * @return Amount of remaining funds
	 * @throws Exception Everything that may go wrong
	 */
	private int availableBalance() throws Exception {
		prepareStatements();

		getUser.clearParameters();
		getUser.setString(1, this.username);
		ResultSet user = getUser.executeQuery();
		user.next();
		int availableBalance = user.getInt("initAmount");
		getUser.close();

		return availableBalance;
	}

	/**
	 * Search direct flights and add those flights to the referenced list
	 *
	 * @param originCity          Origin city, state
	 * @param destinationCity     Destination city, state
	 * @param dayOfMonth          Day of month of travel
	 * @param numberOfItineraries Number of results being shown
	 * @param list                Where the results are stored
	 * @throws Exception Everything that may go wrong
	 */
	private void setDirectSearch(String originCity, String destinationCity, int dayOfMonth, int numberOfItineraries, List<DirectFlight> list) throws Exception {
		directSearch.clearParameters();
		directSearch.setInt(1, numberOfItineraries);
		directSearch.setString(2, originCity);
		directSearch.setString(3, destinationCity);
		directSearch.setInt(4, dayOfMonth);

		ResultSet results = directSearch.executeQuery();
		while (results.next()) {

			int result_fid = results.getInt("fid");
			int result_dayOfMonth = results.getInt("day_of_month");
			String result_carrierId = results.getString("carrier_id");
			String result_flightNum = results.getString("flight_num");
			String result_originCity = results.getString("origin_city");
			String result_destCity = results.getString("dest_city");
			int result_time = results.getInt("actual_time");
			int result_capacity = results.getInt("capacity");
			int result_price = results.getInt("price");

			DirectFlight flight =
					new DirectFlight(result_fid, result_dayOfMonth, result_carrierId, result_flightNum, result_originCity,
							result_destCity, result_time, result_capacity, result_price);

			list.add(flight);
		}

		directSearch.close();
	}

	/**
	 * Search indirect flights and add those flights to the referenced list
	 *
	 * @param originCity          Origin city, state
	 * @param destinationCity     Destination city, state
	 * @param dayOfMonth          Day of month of travel
	 * @param numberOfItineraries Number of results being shown
	 * @param list                Where the results are stored
	 * @throws Exception Everything that may go wrong
	 */
	private void setIndirectSearch(String originCity, String destinationCity, int dayOfMonth, int numberOfItineraries, List<IndirectFlight> list) throws Exception {
		indirectSearch.clearParameters();
		indirectSearch.setInt(1, numberOfItineraries);
		indirectSearch.setString(2, originCity);
		indirectSearch.setString(3, destinationCity);
		indirectSearch.setInt(4, dayOfMonth);
		indirectSearch.setInt(5, dayOfMonth);

		ResultSet results = indirectSearch.executeQuery();
		while (results.next()) {

			int result_fid1 = results.getInt("fid1");
			int result_dayOfMonth1 = results.getInt("day_of_month1");
			String result_carrierId1 = results.getString("carrier_id1");
			String result_flightNum1 = results.getString("flight_num1");
			String result_originCity1 = results.getString("origin_city1");
			String result_destCity1 = results.getString("dest_city1");
			int result_time1 = results.getInt("actual_time1");
			int result_capacity1 = results.getInt("capacity1");
			int result_price1 = results.getInt("price1");

			int result_fid2 = results.getInt("fid2");
			int result_dayOfMonth2 = results.getInt("day_of_month2");
			String result_carrierId2 = results.getString("carrier_id2");
			String result_flightNum2 = results.getString("flight_num2");
			String result_originCity2 = results.getString("origin_city2");
			String result_destCity2 = results.getString("dest_city2");
			int result_time2 = results.getInt("actual_time2");
			int result_capacity2 = results.getInt("capacity2");
			int result_price2 = results.getInt("price2");

			IndirectFlight flight = new IndirectFlight(
					new DirectFlight(result_fid1, result_dayOfMonth1, result_carrierId1, result_flightNum1,
							result_originCity1, result_destCity1, result_time1, result_capacity1, result_price1),
					new DirectFlight(result_fid2, result_dayOfMonth2, result_carrierId2, result_flightNum2,
							result_originCity2, result_destCity2, result_time2, result_capacity2, result_price2));

			list.add(flight);
		}

		indirectSearch.close();
	}

	/**
	 * Check if there is an existing user with the same username in the database
	 *
	 * @param username New username
	 * @return True if the new username is not used; false otherwise
	 * @throws Exception Catches everything that may go wrong
	 */
	private boolean checkUser(String username) throws Exception {
		checkUser.clearParameters();
		checkUser.setString(1, username);
		ResultSet result = checkUser.executeQuery();
		result.next();
		int n = result.getInt("n");
		checkUser.close();

		return n == 0;
	}

	/**
	 * Check if the current session user is logged in
	 *
	 * @return True if logged in; false otherwise
	 */
	private boolean loggedIn() {
		return this.username != null;
	}

}
