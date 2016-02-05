import java.util.ArrayList;
import java.util.NoSuchElementException;

public class Heap<T extends Comparable<T>> {
	
	public static void main(String[] args){
		Heap<Integer> p = new Heap<Integer>();
		int[] tests ={5,7,2,54,43,3,65,2,3,4};
		for(int i=0 ; i<tests.length ; i++){
			p.insert(tests[i]);
		}
		
		for(int i=0 ; i<tests.length ; i++){
			int max = p.delete();
			System.out.print(max + " ");
		}
	}
	
	private ArrayList<T> items;
	
	public Heap(){
		items = new ArrayList<T>();
	}
	
	private void shiftUp(){
		int k=items.size()-1;
		while(k>0){
			int p = (k-1)/2;
			T item = items.get(k);
			T parent = items.get(p);
			if(item.compareTo(parent)>0){
				items.set(p, item);
				items.set(k, parent);
				k = p;
			}
			else break;
		}
	}
	
	public void insert(T item){
		items.add(item);
		shiftUp();
	}
	
	private void shiftDown(){
		int k=0;
		int l=2*k+1;
		while(l<items.size()){
			int max = l, r = l+1;
			if(r < items.size()){
				if(items.get(r).compareTo(items.get(l))>0){
					max = r;
				}
			}
			if(items.get(max).compareTo(items.get(k))>0){
				T temp = items.get(k);
				items.set(k, items.get(max));
				items.set(max, temp);
				k = max;
				l = 2*k+1;
			}
			else break;
		}
	}
	
	public T delete() throws NoSuchElementException
	{
		if(items.size()==0) throw new NoSuchElementException();
		if(items.size()==1) return items.remove(0);
		T hold = items.get(0);
		items.set(0, items.remove(items.size()-1));
		shiftDown();
		return hold;
	}
	
	public int size(){
		return items.size();
	}
	
	public boolean isEmpty(){
		return items.isEmpty();
	}
}
