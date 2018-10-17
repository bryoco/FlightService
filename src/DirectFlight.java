/**
 * Inner class that construct a single flight record
 */

@SuppressWarnings({"WeakerAccess", "unused"})
public class DirectFlight implements Flight, Comparable<Flight> {
	private int fid;             //i=0
	private int dayOfMonth;      //  1
	private String carrierId;    //  2
	private String flightNum;    //  3
	private String originCity;   //  4
	private String destCity;     //  5
	private int time;            //  6
	private int capacity;        //  7
	private int price;           //  8

	DirectFlight(int fid, int dayOfMonth, String carrierId, String flightNum, String originCity, String destCity,
	             int time, int capacity, int price) {
		this.fid = fid;
		this.dayOfMonth = dayOfMonth;
		this.carrierId = carrierId;
		this.flightNum = flightNum;
		this.originCity = originCity;
		this.destCity = destCity;
		this.time = time;
		this.capacity = capacity;
		this.price = price;
	}

	public Object[] makeRecord() {
		return new Object[]{this.fid, this.dayOfMonth, this.carrierId, this.flightNum, this.originCity, this.destCity,
				this.time, this.capacity, this.price};
	}

	public String toString() {
		return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId +
				" Number: " + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time +
				" Capacity: " + capacity + " Price: " + price + "\n";
	}

	public int getFlightType() {
		return 1;
	}

	public int getFid() {
		return fid;
	}

	public int getDayOfMonth() {
		return dayOfMonth;
	}

	public String getCarrierId() {
		return carrierId;
	}

	public String getFlightNum() {
		return flightNum;
	}

	public String getOriginCity() {
		return originCity;
	}

	public String getDestCity() {
		return destCity;
	}

	public int getTime() {
		return time;
	}

	@Override
	public int compareTo() {
		return getTime();
	}

	@Override
	public int compareTo(Flight o) {
		return o.getTime() - this.getTime();
	}

	public int getCapacity() {
		return capacity;
	}

	public int getPrice() {
		return price;
	}


}
