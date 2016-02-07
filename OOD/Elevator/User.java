public class User{
	private String name;
	public User(String n){
		name = n;
	}
	public void pressButton(int toFloor){
		Request request = new Request(toFloor);
		RequestProcessCenter  center = RequestProcessCenter.getInstance();
		center.addRequest(request);
	}
}