import java.util.*;
public class parkingLot {
	private Level[] levels;
	private final int num_levels = 5;
	public parkingLot(){
		
	}
	public boolean parkVehicle(Vehicle vehicle){
		return true;
	}
	
	public enum VehicelSize{Moto, compact, bus}
	
	public abstract class Vehicle{
		protected ArrayList<ParkingSpot> parkingSpots = new ArrayList<ParkingSpot>();
		protected String licensePlate;
		protected int spotsNeeded;
		protected VehicelSize size;
		
		public int getSpotNeeded(){return spotsNeeded;}
		public VehicelSize getSize(){return size;}
		public void parkInSpot(ParkingSpot s){parkingSpots.add(s);}
		public void clearSpots(){parkingSpots = new ArrayList<ParkingSpot>();}
		public abstract boolean canFitInSpot(ParkingSpot spot);
	}
	
	public class Bus extends Vehicle{
		public Bus(){
			spotsNeeded = 5;
			size = VehicelSize.bus;
		}
		
		public boolean canFitInSpot(ParkingSpot spot){
			if(spot.spotSize == VehicelSize.bus && spot.isAvailable()){
				return true;
			}
			return false;
		}
	}
	
	public class Car extends Vehicle{
		public Car(){
			spotsNeeded = 1;
			size = VehicelSize.compact;
		}
		
		public boolean canFitInSpot(ParkingSpot spot){
			return spot.isAvailable();
		}
	}
	
	public class Moto extends Vehicle{
		public Moto(){
			spotsNeeded = 1;
			size = VehicelSize.Moto;
		}
		
		public boolean canFitInSpot(ParkingSpot spot){
			return spot.isAvailable();
		}
	}
	
	public class Level{
		private int floor;
		private ParkingSpot[] spots;
		private int availableSpot = 0;
		private static final int SPOT_ROW = 10;
		
		public Level(int f, int numberSpots){
			floor = f;
			availableSpot = numberSpots;
		}
		public int availableSpot(){return availableSpot;}
		public boolean parkVehicle(Vehicle vehicle){
			availableSpot -= vehicle.spotsNeeded;
			return availableSpot>=0;
		}
		public void spotFreed(){availableSpot++;}
	}
	
	public class ParkingSpot{
		private Vehicle vehicle;
		private VehicelSize spotSize;
		private int row;
		private int spotNumber;
		private Level level;
		
		public ParkingSpot(Level l, int r, int n, Vehicle v){
			level = l;
			row = r;
			spotNumber = n;
			vehicle = v;
		}
		
		public boolean isAvailable(){return vehicle == null;}
		public boolean canFitVehicle(Vehicle vehicle){
			if(vehicle.getSize() == VehicelSize.bus && spotSize != VehicelSize.bus) return false;
			return true;
		}
		public void park(Vehicle v){vehicle = v;}
		public int getRow(){return row;}
		public int getSpotNumber(){return spotNumber;}
		public void removeVehicle(){vehicle=null;}
	}
}