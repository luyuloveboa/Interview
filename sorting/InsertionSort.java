
public class InsertionSort {
	public static void main(String[] args){
		int[] test = new int[]{7, 5, 2, 4, 5, 3, 9};
		insertionSort(test);
		for(int i: test){
			System.out.print(i + " ");
		}
	}
	
	public static void insertionSort(int[] nums){
		if(nums==null || nums.length==0) return;
		
		int len = nums.length;
		int key=0, i=0;
		for(int j=1 ; j<len ; j++){
			key = nums[j];
			i = j-1;
			while(i>=0 && nums[i] > key){
				nums[i+1] = nums[i];
				i--;
			}
			nums[i+1] = key;
		}
	}
}
