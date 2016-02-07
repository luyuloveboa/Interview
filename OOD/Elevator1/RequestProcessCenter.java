import java.util.LinkedList;

public class RequestProcessCenter implements Runnable{
	public LinkedList<Request> q;
	public static RequestProcessCenter instance;
	public RequestProcessCenter(){
		q = new LinkedList<Request>();
	}
	public static RequestProcessCenter getInstance(){
		if(instance==null) instance = new RequestProcessCenter();
		return instance;
	}
	public void addRequest(Request request){
		q.add(request);
	}
	public void removeRequest(Request request){
		q.remove(request);
	}
	public void run(){
		while(true){
			processRequest();
		}
	}
	public Request getNextRequest(){
		Request shortestReq = null;
		int shortest = Integer.MAX_VALUE;
		int curFloor = Elevator.getInstance().getCurrentFloor();
		for(Request r:q){
			int distance = Math.abs(curFloor-r.getToFloor());
			if(distance<shortest){
				shortest = distance;
				shortestReq = r;
			}
		}
		return shortestReq;
	}
	public void processRequest(){
		Request req = getNextRequest();
		if(req!=null){
			int toFloor = req.getToFloor();
			Elevator.getInstance().moveToTargetFloor(toFloor);
			removeRequest(req);
		}
	}
}
