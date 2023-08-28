This is a backup of Perl script from [old plugin wiki](https://wiki.jenkins.io/display/JENKINS/Plot+Plugin).

### Example Usage to generate detailed PMD reports

Following perl script generates more detailed "summaries" out of PMD report files.
```perl
#!/usr/bin/perl

my ($workspace, $baseDir, $prefix, $cyclomaticComplexity, $npathComplexity, $coupling);
$workspace = $ENV{'WORKSPACE'};
$jobName = $ENV{'JOB_NAME'};


if ($jobName = m/trunk\.codeanalysis\.([a-z]+)/) {
  $prefix = $1;
} else {
  die "no prefix specified";
}
print STDERR "team: $prefix\n";

# change this to the project the reports are fetched from
$baseDir="$workspace/pmd";
open(PMD, "<$baseDir/pmd_report_$prefix.xml")|| die "can not open PMD report $baseDir/pmd_report_$prefix.xml: $!";

while(<PMD>) {
  $cyclomaticComplexity += $1 if (/has a Cyclomatic Complexity of (\d+)/);
  $npathComplexity += $1 if (/has an NPath complexity of (\d+)/);
  $coupling += 1 if (/rule="CouplingBetweenObjects"/);
}

foreach('cyclomatic', 'npath', 'coupling') {
  open(OUT, ">$baseDir/pmd_plot_${team}_$_.properties")|| die "can not open output file: $!";

  print OUT "YVALUE=$cyclomaticComplexity\n" if /cyclomatic/;
  print OUT "YVALUE=$npathComplexity\n" if /npath/;
  print OUT "YVALUE=$coupling\n" if /coupling/;
}
```

We use this for several **code analysis** projects, all named `trunk.codeanalysis`.**prefix**.

To have all resulting plots on one page, give all Plots the same **plot group**.
Because each code quality has its very own range, define each on a separate plot, not as an additional data series.
