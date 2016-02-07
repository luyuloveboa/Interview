public class Elevator {
	public enum UpDownEnum{
		up,down
	}
	public static Elevator instance = null;
	private int maxFloor;
	private int currentFloor;
	private UpDownEnum direct;
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
	public UpDownEnum getDirect(){
		return direct;
	}
	public int getCurrentFloor(){
		return currentFloor;
	}
	public void moveToTargetFloor(int toFloor){
		if(currentFloor<toFloor){
			direct = UpDownEnum.up;
		}
		else if(currentFloor>toFloor){
			direct = UpDownEnum.down;
		}
		currentFloor = toFloor;
	}
	public boolean moveUp(){
		direct = UpDownEnum.up;
		if(currentFloor<maxFloor){
			currentFloor++;
			return true;
		}
		return false;
	}
	public boolean moveDown(){
		direct = UpDownEnum.down;
		if(currentFloor>0){
			currentFloor--;
			return true;
		}
		return false;
	}
}
