public class Elevator {
	public static Elevator instance = null;
	private int maxFloor;
	private int currentFloor;
	public Elevator(){}
	public Elevator(int m){
		maxFloor = m;
	}
	public static Elevator getInstance(){
		if(instance == null){
			instance = new Elevator();
		}
		return instance;
	}
	public int getCurrentFloor(){
		return currentFloor;
	}
	public void moveToTargetFloor(int toFloor){
		currentFloor = toFloor;
	}
	public boolean moveUp(){
		if(currentFloor<maxFloor){
			currentFloor++;
			return true;
		}
		return false;
	}
	public boolean moveDown(){
		if(currentFloor>0){
			currentFloor--;
			return true;
		}
		return false;
	}
}
