/**
 * Inner class that construct a single indirect flight pair record
 */
public class IndirectFlight implements Flight, Comparable<Flight> {
	private DirectFlight flight1;
	private DirectFlight flight2;

	IndirectFlight(int fid1, int dayOfMonth1, String carrierId1, String flightNum1, String originCity1, String destCity1,
	               int time1, int capacity1, int price1,
	               int fid2, int dayOfMonth2, String carrierId2, String flightNum2, String originCity2, String destCity2,
	               int time2, int capacity2, int price2) {

		this.flight1 = new DirectFlight(fid1, dayOfMonth1, carrierId1, flightNum1, originCity1, destCity1, time1, capacity1, price1);
		this.flight2 = new DirectFlight(fid2, dayOfMonth2, carrierId2, flightNum2, originCity2, destCity2, time2, capacity2, price2);
	}

	IndirectFlight(DirectFlight f1, DirectFlight f2) {
		this.flight1 = f1;
		this.flight2 = f2;
	}

	public Object[] makeRecord() {
		return new Object[]{this.flight1, this.flight2};
	}

	public String toString() {
		return this.flight1.toString() + this.flight2.toString();
	}

	public int getFlightType() {
		return 2;
	}

	public int getTime() {
		return getFlight1().getTime() + getFlight2().getTime();
	}

	@Override
	public int compareTo() {
		return getTime();
	}

	@Override
	public int compareTo(Flight o) {
		return o.getTime() - this.getTime();
	}

	DirectFlight getFlight1() {
		return flight1;
	}

	DirectFlight getFlight2() {
		return flight2;
	}

}
