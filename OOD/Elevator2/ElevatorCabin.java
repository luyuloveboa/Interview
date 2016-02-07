import java.util.*;

public class ElevatorCabin {
	public enum UpDownEnum{
		UP, DOWN
	}
	public ElevatorCabin instance;
	public int currentFloor = 0;
    public UpDownEnum direction = UpDownEnum.UP;
    public TreeSet<Integer> callingFloorList  = new TreeSet<Integer>();
     
    public void add(int floor){
        callingFloorList.add(floor);
    }
    public ElevatorCabin getInstance(){
    	if(instance==null) instance = new ElevatorCabin();
    	return instance;
    }
    public int gotoNext(){
        int nextFloor = currentFloor;
        switch(direction){
        case UP:
            for (int floor : callingFloorList) {
                if(floor > nextFloor){
                    nextFloor = floor;
                    direction = UpDownEnum.UP;
                    break;
                }
            }
             
            if(nextFloor == currentFloor){
                for (int floor : callingFloorList.descendingSet()) {
                    if(floor < nextFloor){
                        nextFloor = floor;
                        direction = UpDownEnum.DOWN;
                        break;
                    }
                }
            }
            break;
        case DOWN:
            for (int floor : callingFloorList.descendingSet()) {
                if(floor < nextFloor){
                    nextFloor = floor;
                    direction = UpDownEnum.DOWN;
                    break;
                }
            }
             
            if(nextFloor == currentFloor){
                for (int floor : callingFloorList) {
                    if(floor > nextFloor){
                        nextFloor = floor;
                        direction = UpDownEnum.UP;
                        break;
                    }
                }
            }
        }
        callingFloorList.remove(nextFloor);
        currentFloor = nextFloor;
        return nextFloor;
    }
}
