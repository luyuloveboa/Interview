
public class CocktailSort {
	public static void main(String[] args){
		int[] test = new int[]{7, 5, 2, 4, 5, 3, 9};
		//cocktailSort(test);
		cocktailSort2(test);
		for(int i: test){
			System.out.print(i + " ");
		}
	}
	
	private static void swap(int[] nums, int i, int j){
		int tmp = nums[i];
		nums[i] = nums[j];
		nums[j] = tmp;
	}
	
	public static void cocktailSort(int[] nums){
		if(nums==null || nums.length==0) return;
		
		int len = nums.length;
		boolean swap = true;
		
		while(swap){
			swap = false;
			for(int i=0 ; i<len-1 ; i++){
				if(nums[i] > nums[i+1]){
					swap(nums, i, i+1);
					swap = true;
				}
			}
			if(!swap) break;
			
			swap = false;
			for(int i=len-1 ; i>0 ; i--){
				if(nums[i]<nums[i-1]){
					swap(nums, i, i-1);
					swap = true;
				}
			}
		}
	}
	
	public static void cocktailSort2(int[] nums){
		if(nums==null || nums.length==0) return;
		
		int start = 0;
		int end = nums.length-1;
		boolean swap = true;
		
		while(swap){
			swap = false;
			start++;
			for(int i=start ; i<=end ; i++){
				if(nums[i-1] > nums[i]){
					swap(nums, i-1, i);
					swap = true;
				}
			}
			if(!swap) break;
			
			swap = false;
			end--;
			for(int i=end ; i>=start ; i--){
				if(nums[i]<nums[i-1]){
					swap(nums, i, i-1);
					swap = true;
				}
			}
		}
	}
}
