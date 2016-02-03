
public class SelectionSort {
	public static void main(String[] args){
		int[] test = new int[]{7, 5, 2, 4, 5, 3, 9};
		selectionSort(test);
		for(int i: test){
			System.out.print(i+" ");
		}
	}
	
	public static void selectionSort(int[] nums){
		if(nums==null || nums.length==0) return;
		
		int len = nums.length;
		for(int i=0 ; i<len ; i++){
			int minIdx = i;
			//Search the index of smallest element in the remaining array
			for(int j=i+1 ; j<len ; j++){
				if(nums[j] < nums[minIdx]){
					minIdx = j;
				}
			}
			int tmp = nums[i];
			nums[i] = nums[minIdx];
			nums[minIdx] = tmp;
		}
	}
}
