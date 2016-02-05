import java.util.*;

public class ParkingLot2 {
	class Ticket {
        public String PersonName;
        public String TicketID;
        public Ticket(String p, String id){
        	PersonName = p;
        	TicketID = id;
        }
    }
	
	public enum VehicleSize{Small, Medium, Big};
	
    class ValetParking
    {
        private HashMap<Ticket, Spot> ticketRemainder = new HashMap<Ticket, Spot>();

        private List<Spot> smallSpots = new LinkedList<Spot>();
        private List<Spot> mediumSpots = new LinkedList<Spot>();
        private List<Spot> bigSpots = new LinkedList<Spot>();

        public ValetParking(int small, int medium, int big) 
        {
            CreateSpots(small, VehicleSize.Small);
            CreateSpots(medium, VehicleSize.Medium);
            CreateSpots(big, VehicleSize.Big);
        }

        private void CreateSpots(int total, VehicleSize type) {
            List<Spot> list = null;

            switch (type) 
            {
                case Small:
                    {
                        list = smallSpots; break;
                    }
                case Medium:
                    {
                        list = mediumSpots; break;
                    }
                case Big:
                    {
                        list = bigSpots; break;
                    }
            }

            for (int i = 0; i < total; i++)
            {
                list.add(new Spot(type));
            }
        }

        private Spot FindSpot(Vehicle v) 
        {
            if (v == null) return null;

            List<List<Spot>> spotsToSearch = new LinkedList<List<Spot>>();

            spotsToSearch.add(bigSpots);

            if (v.getSize() == VehicleSize.Medium || v.getSize() == VehicleSize.Small) {
                spotsToSearch.add(mediumSpots);
            }
            if (v.getSize() == VehicleSize.Small)
            {
                spotsToSearch.add(smallSpots);
            }

            for (List<Spot> parking : spotsToSearch) 
            {
                for (Spot s : parking)
                {
                    if (!s.isEmpty()) 
                    {
                        return s;
                    }
                }
            }

            return null;
        }

        public Ticket Park(Vehicle v, String personName) {

            Spot s = FindSpot(v);

            if (s != null) {
                s.Park(v);
                Ticket t = new Ticket(personName, "Ticket ID : " + s.hashCode());

                ticketRemainder.put(t, s);

                return t;
            }

            return null;
        } 

        public Vehicle Unpark(Ticket ticket) {
            if (ticket != null && ticketRemainder.containsKey(ticket)) {
                Spot s = ticketRemainder.get(ticket);
                return s.Unpark();
            }

            return null;
        }
    }

    abstract class Vehicle 
    {
        private VehicleSize size;
        String plate;
        public VehicleSize getSize()
        {
            return size;
        }
    }

    class Spot {
        private VehicleSize size = VehicleSize.Small;
        public VehicleSize getSize()
        {
            return size;
        }

        public Spot(VehicleSize vs) {
            size = vs;
        }

        private Vehicle ParkedVehicle;

        public void Park(Vehicle v) 
        {
            if (isEmpty())
            {
                ParkedVehicle = v;
            }
        }

        public Vehicle Unpark() {
            if (!isEmpty()) {
                Vehicle v = ParkedVehicle;
                ParkedVehicle = null;

                return v;
            }

            return null;
        }

        public boolean isEmpty() 
        {
            return ParkedVehicle == null;
        }
    }
}
