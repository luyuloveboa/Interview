public class MergeSort {
	public static void main(String[] args){
		int[] test = new int[]{7, 5, 2, 4, 5, 3, 9};
		mergeSort(test);
		for(int i: test){
			System.out.print(i + " ");
		}
	}
	
	public static void mergeSort(int[] nums){
		if(nums==null || nums.length==0) return;
		
		mergeSortHelper(nums, 0, nums.length-1);
	}
	
	private static void mergeSortHelper(int[] nums, int left, int right){
		if(left < right){
			int mid = left + (right-left)/2;
			mergeSortHelper(nums, left, mid);
			mergeSortHelper(nums, mid+1, right);
			merge(nums, left, mid, right);
		}
	}
	
	private static void merge(int[] nums, int left, int mid, int right){
		int len1 = mid-left+1;
		int len2 = right-mid;
		int[] nums1 = new int[len1+1];
		int[] nums2 = new int[len2+1];
		
		for(int i=0 ; i<len1 ; i++){
			nums1[i] = nums[left+i];
		}
		
		for(int i=0 ; i<len2 ; i++){
			nums2[i] = nums[mid+1+i];
		}
		
		//make sure when one of the list reach the end, if-else statement keeps right
		nums1[len1] = Integer.MAX_VALUE;
		nums2[len2] = Integer.MAX_VALUE;
		int i=0, j=0;
		for(int k=left ; k<=right ; k++){
			if(nums1[i]<=nums2[j]){
				nums[k] = nums1[i];
				i++;
			}
			else{
				nums[k] = nums2[j];
				j++;
			}
		}
	}
}