package crux.bphc.cms.fragments;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import crux.bphc.cms.R;
import crux.bphc.cms.activities.CourseDetailActivity;
import crux.bphc.cms.helper.ClickListener;
import crux.bphc.cms.helper.CourseDataHandler;
import crux.bphc.cms.helper.CourseDownloader;
import crux.bphc.cms.helper.CourseRequestHandler;
import crux.bphc.cms.helper.HtmlTextView;
import crux.bphc.cms.helper.UserUtils;
import crux.bphc.cms.models.Course;
import crux.bphc.cms.models.CourseSection;
import crux.bphc.cms.models.Module;
import crux.bphc.cms.models.forum.Discussion;

import static android.content.Context.INPUT_METHOD_SERVICE;


public class MyCoursesFragment extends Fragment {

    private static final String ARG_PARAM1 = "param1";
    private static final int COURSE_SECTION_ACTIVITY = 105;
    RecyclerView mRecyclerView;
    EditText mSearch;
    SwipeRefreshLayout mSwipeRefreshLayout;
    List<Course> courses;
    View empty;
    ImageView mSearchIcon;
    boolean isClearIconSet = false;
    List<CourseDownloader.DownloadReq> requestedDownloads;
    String mSearchedText = "";
    private String TOKEN;
    private MyAdapter mAdapter;
    private int coursesUpdated;

    private MoreOptionsFragment.OptionsViewModel moreOptionsViewModel;

    public MyCoursesFragment() {
        // Required empty public constructor
    }

    public static MyCoursesFragment newInstance(String token) {
        MyCoursesFragment fragment = new MyCoursesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, token);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            TOKEN = getArguments().getString(ARG_PARAM1);
        }
        courseDataHandler = new CourseDataHandler(getActivity());
    }

    @Override
    public void onStart() {
        if(getActivity() != null) {
            getActivity().setTitle("My Courses");
        }
        super.onStart();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_courses, container, false);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == COURSE_SECTION_ACTIVITY) {
            courses = courseDataHandler.getCourseList();
            filterMyCourses(mSearchedText);
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requestedDownloads = new ArrayList<>();

        empty = view.findViewById(R.id.empty);
        courses = new ArrayList<>();
        courses = courseDataHandler.getCourseList();

        mRecyclerView = view.findViewById(R.id.recyclerView);
        mSearch = view.findViewById(R.id.searchET);
        mSwipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        mSearchIcon = view.findViewById(R.id.searchIcon);

        moreOptionsViewModel = new ViewModelProvider(requireActivity()).get(MoreOptionsFragment.OptionsViewModel.class);
        mAdapter = new MyAdapter(getActivity(), courses);
        mAdapter.setClickListener(new ClickListener() {
            @Override
            public boolean onClick(Object object, int position) {
                Course course = (Course) object;

                Intent intent = new Intent(getActivity(), CourseDetailActivity.class);
                intent.putExtra("courseId", course.getCourseId());
                intent.putExtra("course_name", course.getShortname());
                startActivityForResult(intent, COURSE_SECTION_ACTIVITY);
                return true;
            }
        });


        mAdapter.setCourses(courses);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
//        mSwipeRefreshLayout.setRefreshing(true);
        mSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                mSearchedText = s.toString().toLowerCase().trim();
                filterMyCourses(mSearchedText);

                if (!isClearIconSet) {
                    mSearchIcon.setImageResource(R.drawable.ic_clear_black_24dp);
                    isClearIconSet = true;
                    mSearchIcon.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            mSearch.setText("");
                            mSearchIcon.setImageResource(R.drawable.ic_search);
                            mSearchIcon.setOnClickListener(null);
                            isClearIconSet = false;
                            InputMethodManager inputManager = (InputMethodManager) getActivity().getSystemService(INPUT_METHOD_SERVICE);
                            inputManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                        }
                    });
                }
                if (mSearchedText.isEmpty()) {
                    mSearchIcon.setImageResource(R.drawable.ic_search);
                    mSearchIcon.setOnClickListener(null);
                    isClearIconSet = false;
                }
            }
        });

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mSwipeRefreshLayout.setRefreshing(true);
                makeRequest();
            }
        });

        mAdapter.setDownloadClickListener(new ClickListener() {
            @Override
            public boolean onClick(Object object, final int position) {
                final Course course = (Course) object;
                if (course.getDownloadStatus() != -1)
                    return false;
                course.setDownloadStatus(0);
                mAdapter.notifyItemChanged(position);
                final CourseDownloader courseDownloader = new CourseDownloader(getActivity(),
                        CourseDataHandler.getCourseName(course.getId()));
                courseDownloader.setDownloadCallback(new CourseDownloader.DownloadCallback() {
                    @Override
                    public void onCourseDataDownloaded() {
                        course.setDownloadedFiles(courseDownloader.getDownloadedContentCount(course.getId()));
                        course.setTotalFiles(courseDownloader.getTotalContentCount(course.getId()));
                        if (course.getTotalFiles() == course.getDownloadedFiles()) {
                            Toast.makeText(getActivity(), "All files already downloaded", Toast.LENGTH_SHORT).show();
                            course.setDownloadStatus(-1);
                        } else {
                            course.setDownloadStatus(1);
                        }
                        mAdapter.notifyItemChanged(position);
                    }

                    @Override
                    public void onCourseContentDownloaded() {
                        course.setDownloadedFiles(course.getDownloadedFiles() + 1);

                        if (course.getDownloadedFiles() == course.getTotalFiles()) {
                            course.setDownloadStatus(-1);
                            courseDownloader.unregisterReceiver();
                            //todo notification all files downloaded for this course
                        }
                        mAdapter.notifyItemChanged(position);
                    }

                    @Override
                    public void onFailure() {
                        Toast.makeText(getActivity(), "Check your internet connection", Toast.LENGTH_SHORT).show();
                        courses.get(position).setDownloadStatus(-1);
                        mAdapter.notifyItemChanged(position);
                        courseDownloader.unregisterReceiver();
                    }
                });
                courseDownloader.downloadCourseData(course.getCourseId());

                return true;

            }
        });

        checkEmpty();
        if (courses.isEmpty()) {
            mSwipeRefreshLayout.setRefreshing(true);
            makeRequest();
        }
    }

    private void checkEmpty() {
        if (courses.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
        }
    }

    CourseDataHandler courseDataHandler;

    private void makeRequest() {
        CourseRequestHandler courseRequestHandler = new CourseRequestHandler(getActivity());
        courseRequestHandler.getCourseList(new CourseRequestHandler.CallBack<List<Course>>() {
            @Override
            public void onResponse(List<Course> courseList) {
                courses.clear();
                courses.addAll(courseList);
                checkEmpty();
                filterMyCourses(mSearchedText);
                updateCourseContent(courses);
            }

            @Override
            public void onFailure(String message, Throwable t) {
                mSwipeRefreshLayout.setRefreshing(false);
                if (t.getMessage().contains("Invalid token")) {
                    Toast.makeText(
                            getActivity(),
                            "Invalid token! Probably your token was reset.",
                            Toast.LENGTH_SHORT).show();
                    UserUtils.logoutAndClearBackStack(getActivity());
                    return;
                }
                Toast.makeText(getActivity(), "Unable to connect to server!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCourseContent(List<Course> courses) {
        courseDataHandler.setCourseList(courses);
        CourseRequestHandler courseRequestHandler = new CourseRequestHandler(getActivity());
        coursesUpdated = 0;
        if (courses.size() == 0) mSwipeRefreshLayout.setRefreshing(false);
        for (Course course : courses) {
            courseRequestHandler.getCourseData(course.getCourseId(),
                    new CourseRequestHandler.CallBack<List<CourseSection>>() {
                        @Override
                        public void onResponse(List<CourseSection> responseObject) {
                            for (CourseSection courseSection : responseObject) {
                                List<Module> modules = courseSection.getModules();
                                for (Module module : modules) {
                                    if (module.getModType() == Module.Type.FORUM) {
                                        courseRequestHandler.getForumDiscussions(module.getInstance(), new CourseRequestHandler.CallBack<List<Discussion>>() {
                                            @Override
                                            public void onResponse(List<Discussion> responseObject) {
                                                for (Discussion d : responseObject) {
                                                    d.setForumId(module.getInstance());
                                                }
                                                List<Discussion> newDiscussions = courseDataHandler.setForumDiscussions(module.getInstance(), responseObject);
                                                if (newDiscussions.size() > 0)
                                                    courseDataHandler.markAsReadandUnread(module.getId(), true);
                                            }

                                            @Override
                                            public void onFailure(String message, Throwable t) {
                                                mSwipeRefreshLayout.setRefreshing(false);
                                            }
                                        });
                                    }
                                }
                            }
                            List<CourseSection> newPartsinSections = courseDataHandler.setCourseData(course.getCourseId(), responseObject);
                            if (newPartsinSections.size() > 0) {
                                coursesUpdated++;
                            }
                            //Refresh the recycler view for the last course
                            if (course.getCourseId() == courses.get(courses.size() - 1).getCourseId()) {
                                mSwipeRefreshLayout.setRefreshing(false);
                                mRecyclerView.getAdapter().notifyDataSetChanged();
                                String message;
                                if (coursesUpdated == 0) {
                                    message = getString(R.string.upToDate);
                                } else {
                                    message = getResources().getQuantityString(R.plurals.noOfCoursesUpdated, coursesUpdated, coursesUpdated);
                                }
                                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                            }

                        }

                        @Override
                        public void onFailure(String message, Throwable t) {
                            mSwipeRefreshLayout.setRefreshing(false);
                        }
                    });
        }
    }

    private void filterMyCourses(String searchedText) {
        if (searchedText.isEmpty()) {
            mAdapter.setCourses(courses);

        } else {
            List<Course> filteredCourses = new ArrayList<>();
            for (Course course : courses) {
                if (course.getFullname().toLowerCase().contains(searchedText)) {
                    filteredCourses.add(course);
                }
            }
            mAdapter.setCourses(filteredCourses);
        }
    }

    private class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {

        LayoutInflater inflater;
        Context context;
        ClickListener clickListener;
        ClickListener downloadClickListener;
        private List<Course> mCourseList;

        MyAdapter(Context context, List<Course> courseList) {
            this.context = context;
            inflater = LayoutInflater.from(context);
            mCourseList = courseList;

        }

        void setClickListener(ClickListener clickListener) {
            this.clickListener = clickListener;
        }

        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new MyViewHolder(inflater.inflate(R.layout.row_course, parent, false));
        }

        @Override
        public void onBindViewHolder(MyViewHolder holder, int position) {
            holder.bind(mCourseList.get(position));
        }

        @Override
        public int getItemCount() {
            return mCourseList != null ? mCourseList.size() : 0;
        }

        void setCourses(List<Course> courseList) {
            mCourseList = courseList;
            for (int i = 0; i < mCourseList.size(); i++) {
                mCourseList.get(i).setDownloadStatus(-1);
            }
            notifyDataSetChanged();
        }

        public void setDownloadClickListener(ClickListener downloadClickListener) {
            this.downloadClickListener = downloadClickListener;
        }

        class MyViewHolder extends RecyclerView.ViewHolder {

            HtmlTextView courseName1;
            HtmlTextView courseName2;
            View rowClickWrapper;
            ImageView more_options;
            ProgressBar progressBar;
            TextView unreadCount;


            MyViewHolder(View itemView) {
                super(itemView);
                courseName1 = itemView.findViewById(R.id.courseName1);
                courseName2 = itemView.findViewById(R.id.courseName2);
                progressBar = itemView.findViewById(R.id.progressBar);
                more_options = itemView.findViewById(R.id.more_options_button);
                unreadCount = itemView.findViewById(R.id.unreadCount);
                rowClickWrapper = itemView.findViewById(R.id.rowClickWrapper);

                rowClickWrapper.setOnClickListener(view -> {
                    if (clickListener != null) {
                        int pos = getLayoutPosition();
                        clickListener.onClick(mCourseList.get(pos), pos);
                    }
                });

                itemView.setOnClickListener(view -> {
                    if (clickListener != null) {
                        int pos = getLayoutPosition();
                        clickListener.onClick(mCourseList.get(pos), pos);
                    }
                });

                more_options.setOnClickListener(view -> {
                    MoreOptionsFragment.OptionsViewModel moreOptionsViewModel = MyCoursesFragment.this.moreOptionsViewModel;
                    Observer<MoreOptionsFragment.Option> observer;  // to handle the selection
                    //Set up our options and their handlers
                    ArrayList<MoreOptionsFragment.Option> options = new ArrayList<>();
                    options.addAll(Arrays.asList(
                            new MoreOptionsFragment.Option(0, "Download course", R.drawable.download),
                            new MoreOptionsFragment.Option(1, "Mark all as read", R.drawable.eye)
                    ));

                    observer = option -> {
                        if (option == null) return;
                        switch (option.getId()) {
                            case 0:
                                confirmDownloadCourse();
                                break;

                            case 1:
                                markAllAsRead(getLayoutPosition());
                                break;
                        }
                        moreOptionsViewModel.getSelection().removeObservers((AppCompatActivity) context);
                        moreOptionsViewModel.clearSelection();
                    };

                    String courseName = courses.get(getLayoutPosition()).getShortname();
                    MoreOptionsFragment moreOptionsFragment = MoreOptionsFragment.newInstance(courseName, options);
                    moreOptionsFragment.show(((AppCompatActivity) context).getSupportFragmentManager(), moreOptionsFragment.getTag());
                    moreOptionsViewModel.getSelection().observe((AppCompatActivity) context, observer);
                });
            }


            void bind(Course course) {
                courseName1.setText(course.getCourseName()[0]);
                String name = course.getCourseName()[1] + " " + course.getCourseName()[2];
                courseName2.setText(name);
                /*if (course.getDownloadStatus() == -1) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    //the course is downloading and is in midway
                    progressBar.setVisibility(View.VISIBLE);
                    downloadIcon.setVisibility(View.INVISIBLE);
                    if (course.getDownloadStatus() == 0)   // downloading section data
                        downloadText.setText("Downloading course information... ");
                    else if (course.getDownloadStatus() == 1)
                        downloadText.setText("Downloading files... ( " + course.getDownloadedFiles() + " / " + course.getTotalFiles() + " )");
                    else
                        downloadText.setText("Downloaded");
                }*/
                int count = courseDataHandler.getUnreadCount(course.getId());
                unreadCount.setText(Integer.toString(count));
                unreadCount.setVisibility(count == 0 ? View.INVISIBLE : View.VISIBLE);
            }

            void confirmDownloadCourse() {
                new MaterialAlertDialogBuilder(context)
                        .setTitle("Confirm Download")
                        .setMessage("Are you sure you want to all the contents of this course?")
                        .setPositiveButton("Yes", (dialogInterface, i) -> {
                            if (downloadClickListener != null) {
                                int pos = getLayoutPosition();
                                if (!downloadClickListener.onClick(courses.get(pos), pos)) {
                                    Toast.makeText(getActivity(), "Download already in progress", Toast.LENGTH_SHORT).show();
                                }
                            }
                         })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            public void markAllAsRead(int position){
                int courseId = courses.get(position).getCourseId();
                List<CourseSection> courseSections;
                courseSections = courseDataHandler.getCourseData(courseId);
                courseDataHandler.markAllAsRead(courseSections);
                courseSections = courseDataHandler.getCourseData(courseId);
                int count = courseDataHandler.getUnreadCount(courses.get(position).getId());
                unreadCount.setText(Integer.toString(count));
                unreadCount.setVisibility(count == 0 ? View.INVISIBLE : View.VISIBLE);
                Toast.makeText(getActivity(), "Marked all as read", Toast.LENGTH_SHORT).show();
            }
        }

    }


}
