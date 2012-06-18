#!/usr/bin/perl

use Cwd;
use File::Basename;
use File::Find;
use File::Spec;

# Set working dir to the root dir of the repo.  Assumes that this script file lives in
# [Repo root dir]/tools.
my $sdk_root_dir = dirname( __FILE__ );
chdir $sdk_root_dir || die "Can't chdir to $sdk_root_dir: $!\n";
chdir ".." || die "Can't chdir to parent of $sdk_root_dir: $!\n";
$sdk_root_dir = getcwd();

# Create the list of source and destination files from symlinks.
my %src_to_dest_map;
find(\&wanted, ( $sdk_root_dir ));

# Write those file combinations to a text file, for further processing by a Windows script.
my $out_file = File::Spec->catfile($sdk_root_dir, "tools", "symlink_files.txt");
open(my $out_fh, ">:encoding(UTF-8)", $out_file) || die "Can't open $out_file for writing: $!\n";
my $src_file;
foreach $src_file (sort keys %src_to_dest_map) {
    for my $dest_file (sort @{ $src_to_dest_map{$src_file} }) {
        print $out_fh "\"$src_file\" \"$dest_file\"\r\n";
    }
}
close $out_fh || die "Could not close filehandle to $out_file: $!\n";


# Subroutine that processes symlink files.
sub wanted {
    # Look for symbolic link files.
    my $abs_dest_file_path = $File::Find::name;
    if (-l $abs_dest_file_path) {
        # Build the absolute source file path from the linked file information.
        my $src_file_path = readlink($File::Find::name);
        my ($src_file_name, $src_file_dir) = fileparse($src_file_path);
        chdir $src_file_dir || die "Can't change to src dir path $src_file_dir: $!\n";
        my $abs_src_file_dir = getcwd();
        my $abs_src_file_path = File::Spec->catfile($abs_src_file_dir, $src_file_name);
        if (! -e "$abs_src_file_path") {
            die "ERROR: '$abs_src_file_path' is the source file linked to by '$abs_dest_file_path', but it doesn't exist!\n";
        }
        chdir $File::Find::dir || die "Could not chdir back to orig dir ${File::Find::dir}: $!\n";

        # Make relative paths to the root of the repo out of the source and destination entries.
        my $rel_src_file_path = $abs_src_file_path;
        $rel_src_file_path =~ s/^$sdk_root_dir[\/\\]?//g;
        my $rel_dest_file_path = $abs_dest_file_path;
        $rel_dest_file_path =~ s/^$sdk_root_dir[\/\\]?//g;

        # Turn the paths into Windows paths.
        $rel_src_file_path =~ s/\//\\/g;
        $rel_dest_file_path =~ s/\//\\/g;

        # Add the entries to the associative array.
        if (!defined($src_to_dest_map{ $rel_src_file_path })) {
            $src_to_dest_map{ $rel_src_file_path } = [ $rel_dest_file_path ];
        } else {
            push @{ $src_to_dest_map{$rel_src_file_path} }, $rel_dest_file_path;
        }
    }
}
