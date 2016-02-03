
public class OddEvenSort {
	public static void main(String[] args){
		int[] test = new int[]{7, 5, 2, 4, 5, 3, 9};
		oddEvenSort(test);
		for(int i: test){
			System.out.print(i + " ");
		}
	}
	
	private static void swap(int[] nums, int i, int j){
		int tmp = nums[i];
		nums[i] = nums[j];
		nums[j] = tmp;
	}
	
	public static void oddEvenSort(int[] nums){
		if(nums==null || nums.length==0) return;
		
		int len = nums.length;
		boolean sorted  = false;
		
		while(!sorted){
			sorted = true;
			for(int i=1 ; i<len-1 ; i+=2){
				if(nums[i]>nums[i+1]){
					sorted = false;
					swap(nums, i, i+1);
				}
			}
			
			for(int i=0 ; i<len-1 ; i+=2){
				if(nums[i]>nums[i+1]){
					sorted = false;
					swap(nums, i, i+1);
				}
			}
		}
	}
}
