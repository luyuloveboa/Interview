/*
 * Quicksort is a divide and conquer algorithm. It first divides a large list into 
 * two smaller sub-lists and then recursively sort the two sub-lists. If we want to 
 * sort an array without any extra space, quicksort is a good option. On average, 
 * time complexity is O(n log(n)).
 */


public class QuickSort {
	public static void main(String[] args){
		int[] test = new int[]{7, 5, 2, 4, 5, 3, 9};
		quickSort(test);
		for(int i: test){
			System.out.print(i + " ");
		}
	}
	
	public static void quickSort(int[] nums){
		if(nums==null || nums.length==0) return;
		
		int len = nums.length;
		quickSortHelper(nums, 0, len-1);
	}
	
	private static void quickSortHelper(int[] nums, int start, int end){
		if(start>=end) return;
		
		int mid = start + (end-start)/2;
		int pivot = nums[mid];
		
		int i=start, j=end;
		while(i<=j){
			while(nums[i]<pivot){
				i++;
			}
			while(nums[j]>pivot){
				j--;
			}
			if(i<=j){
				int tmp = nums[i];
				nums[i] = nums[j];
				nums[j] = tmp;
				i++;
				j--;
			}
		}
		
		if(start<j) quickSortHelper(nums, start, j);
		if(i<end) quickSortHelper(nums, i, end);
	}
}
