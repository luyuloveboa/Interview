
public class BubbleSort {
	public static void main(String[] args){
		int[] test = new int[]{7, 5, 2, 4, 5, 3, 9};
		//bubbleSort(test);
		//bubbleSort2(test);
		//bubbleSort3(test);
		bubbleSort4(test);
		for(int i: test){
			System.out.println(i);
		}
	}
	
	private static void swap(int[] nums, int i, int j){
		int tmp = nums[i];
		nums[i] = nums[j];
		nums[j] = tmp;
	}
	
	public static void bubbleSort(int[] nums){
		if(nums==null || nums.length==0) return;
		
		int len = nums.length;
		for(int i=len-1 ; i>=0 ; i--){
			for(int j=1 ; j<=i ; j++){
				if(nums[j-1] > nums[j]){
					swap(nums, j-1, j);
				}
			}
		}
	}
	
	public static void bubbleSort2(int[] nums){
		if(nums==null || nums.length==0) return;
		int len = nums.length;
		boolean swap = true;
		
		while(swap){
			swap = false;
			for(int i=1 ; i<len ; i++){
				if(nums[i-1]>nums[i]){
					swap = true;
					swap(nums, i-1, i);
				}
			}
		}
	}
	
	public static void bubbleSort3(int[] nums){
		if(nums==null || nums.length==0) return;
		int len = nums.length;
		boolean swap = true;
		
		while(swap){
			swap = false;
			for(int i=1 ; i<len ; i++){
				if(nums[i-1]>nums[i]){
					swap = true;
					swap(nums, i-1, i);
				}
			}
			len--; //Last element has already sorted
		}
	}
	
	public static void bubbleSort4(int[] nums){
		if(nums==null || nums.length==0) return;
		int len = nums.length;
		
		while(len != 0){
			int lastSwap = 0;
			for(int i=1 ; i<len ; i++){
				if(nums[i-1]>nums[i]){
					swap(nums, i-1, i);
					lastSwap = i;
				}
			}
			len=lastSwap; //The last swap is the place already sorted.
		}
	}
}
