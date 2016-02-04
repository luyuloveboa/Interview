
public class HeapSort {
	public static void main(String[] args){
		int[] test = new int[]{7, 5, 2, 4, 5, 3, 9};
		heapSort(test);
		for(int i: test){
			System.out.print(i + " ");
		}
	}
	
	private static int N;
	
	private static void heapSort(int[] nums){
		heapify(nums);
		for(int i=N ; i>0 ; i--){
			swap(nums, 0, i);
			N = N-1;
            maxheap(nums, 0);
		}
	}
	
	private static void heapify(int arr[])
    {
        N = arr.length-1;
        for (int i = N/2; i >= 0; i--)
            maxheap(arr, i);        
    }
	
	public static void maxheap(int arr[], int i)
    { 
        int left = 2*i ;
        int right = 2*i + 1;
        int max = i;
        if (left <= N && arr[left] > arr[i])
            max = left;
        if (right <= N && arr[right] > arr[max])        
            max = right;
 
        if (max != i)
        {
            swap(arr, i, max);
            maxheap(arr, max);
        }
    }
	
	public static void swap(int arr[], int i, int j)
    {
        int tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp; 
    }
}
